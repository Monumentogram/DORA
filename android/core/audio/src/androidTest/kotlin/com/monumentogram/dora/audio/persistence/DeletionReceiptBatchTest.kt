package com.monumentogram.dora.audio.persistence

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.persistence.auth.LocalAccessDeniedException
import com.monumentogram.dora.audio.persistence.journal.AudioDeletionTargetKind
import com.monumentogram.dora.audio.persistence.journal.RoomAudioJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionReceiptBatchTest {
    @Test
    fun revocationDuringFinalAbsenceCheckCannotCommitReceipts() {
        val fixture = EncryptedAudioVaultFaultFixture()
        var database: SupportSQLiteDatabase? = null
        var locked = false
        var removed = 0
        val dependencies =
            captured({ database = it }) { phase ->
                if (phase == "KEY_REMOVED" && ++removed == 3) error("SYNTHETIC_BEFORE_RECEIPTS")
            }
        val opened =
            EncryptedAudioVault.open(
                fixture.context,
                true,
                { if (locked) throw LocalAccessDeniedException() },
                { it() },
                dependencies,
            )
        assertTrue(opened is AudioResult.Value)
        (opened as AudioResult.Value).value.use { vault ->
            populate(fixture, vault)
            assertEquals(
                AudioResult.Failed(AudioFailure.UNAVAILABLE),
                vault.deleteConfirmed(fixture.audio),
            )
            assertEquals(3, removed)
            val field =
                EncryptedAudioVault::class.java.getDeclaredField("journal").apply {
                    isAccessible = true
                }
            val journal = field.get(vault) as RoomAudioJournal
            checkNotNull(journal.catalog.tryAcquire(fixture.audio)).use {
                val targets =
                    journal
                        .pendingDeletionPage(fixture.audio, AudioDeletionTargetKind.KEY_REFERENCE)
                        .map { it.target }
                assertEquals(3, targets.size)
                var checked = 0
                assertThrows(LocalAccessDeniedException::class.java) {
                    journal.completeDeletionTargets(fixture.audio, targets) {
                        if (++checked == targets.size) locked = true
                        true // All three real keys were already removed before the injected crash.
                    }
                }
                assertEquals(0, receipts(checkNotNull(database), true))
                locked = false
            }
        }
        fixture.open(false).use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
        }
    }

    @Test
    fun crashBeforeBatchReceiptReplaysAlreadyAbsentKeysAfterReopen() {
        val fixture = EncryptedAudioVaultFaultFixture()
        var database: SupportSQLiteDatabase? = null
        var removed = 0
        val dependencies =
            captured({ database = it }) { phase ->
                if (phase == "KEY_REMOVED" && ++removed == 2) error("SYNTHETIC_BATCH_INTERRUPTION")
            }
        fixture.open(dependencies = dependencies).use { vault ->
            populate(fixture, vault)
            assertEquals(
                AudioResult.Failed(AudioFailure.UNAVAILABLE),
                vault.deleteConfirmed(fixture.audio),
            )
            assertEquals(2, removed)
            assertEquals(0, receipts(checkNotNull(database), completed = true))
            assertTrue(receipts(checkNotNull(database), completed = false) >= 3)
        }
        var replayedKeys = 0
        fixture
            .open(
                false,
                captured({ database = it }) { phase ->
                    if (phase == "KEY_REMOVED") replayedKeys++
                    if (phase == "FILE_REMOVED")
                        assertEquals(0, receipts(checkNotNull(database), false))
                },
            )
            .use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
                assertTrue("Absent keys must be retried idempotently", replayedKeys >= 3)
                assertEquals(
                    AudioResult.Value(AudioSourceState.UserDeleted),
                    vault.sourceState(fixture.audio),
                )
            }
    }

    @Test
    fun revocationBetweenEffectsStopsBatchAndKeyReceiptsPrecedeArtifacts() {
        val fixture = EncryptedAudioVaultFaultFixture()
        var database: SupportSQLiteDatabase? = null
        var locked = false
        var removed = 0
        var revoke = true
        val dependencies =
            captured({ database = it }) { phase ->
                if (phase == "KEY_REMOVED") {
                    removed++
                    if (revoke) locked = true
                }
                if (phase == "FILE_REMOVED")
                    assertEquals(0, receipts(checkNotNull(database), false))
            }
        val opened =
            EncryptedAudioVault.open(
                fixture.context,
                true,
                { if (locked) throw LocalAccessDeniedException() },
                { it() },
                dependencies,
            )
        assertTrue(opened is AudioResult.Value)
        (opened as AudioResult.Value).value.use { vault ->
            populate(fixture, vault)
            assertEquals(
                AudioResult.Failed(AudioFailure.LOCKED),
                vault.deleteConfirmed(fixture.audio),
            )
            assertEquals("No second effect after revocation", 1, removed)
            assertEquals(0, receipts(checkNotNull(database), true))
            locked = false
            revoke = false
            EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
            assertEquals(
                AudioResult.Value(AudioSourceState.UserDeleted),
                vault.sourceState(fixture.audio),
            )
        }
    }

    private fun populate(fixture: EncryptedAudioVaultFaultFixture, vault: EncryptedAudioVault) {
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
    }

    private fun receipts(database: SupportSQLiteDatabase, completed: Boolean): Int =
        database
            .query(
                "SELECT COUNT(*) FROM deletion_target WHERE kind='KEY_REFERENCE' AND completed=?",
                arrayOf(if (completed) 1 else 0),
            )
            .use {
                check(it.moveToFirst())
                it.getInt(0)
            }

    private fun captured(database: (SupportSQLiteDatabase) -> Unit, step: (String) -> Unit) =
        EncryptedAudioVault.Dependencies(
            deletionStep = step,
            helperFactory = { delegate ->
                SupportSQLiteOpenHelper.Factory { config ->
                    val helper = delegate.create(config)
                    object : SupportSQLiteOpenHelper by helper {
                        override val writableDatabase
                            get() = helper.writableDatabase.also(database)
                    }
                }
            },
        )
}
