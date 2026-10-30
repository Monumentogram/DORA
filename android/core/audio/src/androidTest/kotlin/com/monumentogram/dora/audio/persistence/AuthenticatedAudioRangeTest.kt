package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticatedAudioRangeTest {
    @Test
    fun deletionCompletionDoesNotReloadAllTargetRowsForEveryArtifact() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val metrics = AudioQueryMetrics()
        val dependencies = EncryptedAudioVault.Dependencies(helperFactory = metrics::factory)
        fixture.open(dependencies = dependencies).use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            repeat(3) { ordinal ->
                EncryptedAudioVaultFaultFixture.success(
                    fixture.append(
                        vault,
                        fixture.unit.copy(
                            unitId = EncryptedAudioVaultFaultFixture.id(),
                            ordinal = ordinal,
                            firstFrame = ordinal * 160L,
                            sourceFrameOffset = ordinal * 160L,
                        ),
                    )
                )
            }
            EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(fixture.audio))
            val before = metrics.deletionBulkQueries.get()
            val rowsBefore = metrics.deletionRows.get()
            val transactionsBefore = metrics.transactions.get()
            EncryptedAudioVaultFaultFixture.success(vault.deleteConfirmed(fixture.audio))
            assertTrue(
                "Deletion must read whole target inventory only a bounded number of times",
                metrics.deletionBulkQueries.get() - before <= 8,
            )
            assertTrue(
                "Deletion row reads must grow linearly with the 21 frozen targets",
                metrics.deletionRows.get() - rowsBefore <= 21 * 20,
            )
            assertTrue(
                "Receipt commits must be batched by deletion phase",
                metrics.transactions.get() - transactionsBefore <= 32,
            )
        }
    }

    @Test
    fun selectedCiphertextIsReauthenticatedAndDeletionFencesOpenHandle() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
            EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(fixture.audio))
            val ref =
                ((vault.originals.acquire(fixture.audio) as AudioResult.Value).value
                        as OriginalAudioStatus.Available)
                    .reference
            val handle = (vault.originals.openAuthenticated(ref) as AudioResult.Value).value
            handle.use {
                var bytes = 0
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.Available(ref)),
                    it.extractRange(159, 1) { first, pcm ->
                        assertEquals(159L, first)
                        bytes += pcm.size
                    },
                )
                assertEquals(2, bytes)
                val ciphertext = File(fixture.run(), RecoveryRelativeNames.microfileCiphertext(0UL))
                val changed = ciphertext.readBytes()
                changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
                ciphertext.writeBytes(changed)
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceUnavailable(AudioFailure.CORRUPT)),
                    it.extractRange(0, 1) { _, _ -> error("Corrupt PCM") },
                )
                assertTrue(vault.deleteConfirmed(fixture.audio) is AudioResult.Value)
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                    it.extractRange(0, 1) { _, _ -> error("Deleted PCM") },
                )
            }
        }
    }
}
