@file:Suppress("MagicNumber", "TooManyFunctions")

package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeystoreIo
import java.io.File
import java.security.MessageDigest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyntheticVaultPreservationTest {
    @Test
    fun unfinalizedAppliedAppendAuthenticatesEverySampleAcrossBoundedCapRotations() {
        // LONG01/LONG02-shaped equivalents: complete CAPs followed by a partial CAP. These are
        // structural tests (3+3+1 and 2+2+1), not claims of historical duration or private IDs.
        for ((blocks, cap) in listOf(7 to 3, 5 to 2)) {
            val fixture = SyntheticPreservationFixture(blocks, cap)
            fixture.prepare()
            fixture.unchanged {
                val copy = fixture.acquire()
                val receipt = fixture.inspect(copy)
                assertEquals(blocks, receipt.authenticatedBlocks)
                assertEquals(
                    blocks * SyntheticPreservationFixture.FRAMES.toLong(),
                    receipt.authenticatedFrames,
                )
                fixture.assertPcmPrefix(copy)
            }
        }
    }

    @Test
    fun absentIntentAuthenticatesWithoutRecreatingIntentOrKeys() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        fixture.mutate("DELETE FROM audio_intent")
        fixture.unchanged {
            val copy = fixture.acquire()
            assertEquals(fixture.blocks, fixture.inspect(copy).authenticatedBlocks)
            fixture.assertPcmPrefix(copy)
            fixture.database(copy.context) { db ->
                assertEquals(0L, fixture.scalar(db, "SELECT count(*) FROM audio_intent"))
            }
        }
    }

    @Test
    fun finalizedSourceIsRejectedWithoutClearingFinalization() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare(finalized = true)
        fixture.unchanged {
            val copy = fixture.acquire()
            assertThrows(IllegalStateException::class.java) { fixture.inspect(copy) }
            fixture.database(copy.context) { db ->
                assertEquals(
                    1L,
                    fixture.scalar(
                        db,
                        "SELECT finalized FROM audio_asset WHERE assetId='${fixture.real.audio.assetId.value}'",
                    ),
                )
            }
            fixture.assertPcmPrefix(copy)
        }
    }

    @Test
    fun pendingAppendIsRejectedEvenWhenCiphertextAndPrefixAreAuthentic() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        fixture.mutate("UPDATE audio_intent SET applied=0")
        fixture.unchanged {
            val copy = fixture.acquire()
            assertThrows(IllegalStateException::class.java) { fixture.inspect(copy) }
            fixture.assertPcmPrefix(copy)
            fixture.database(copy.context) { db ->
                assertEquals(0L, fixture.scalar(db, "SELECT applied FROM audio_intent"))
            }
        }
    }

    @Test
    fun interruptedAppendRetainsAuthenticatedPrefixButWholeInspectionStopsAtIncompleteTail() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        lateinit var fault: CandidateFault
        fixture.real
            .open(
                false,
                EncryptedAudioVault.Dependencies(
                    candidateStorage = {
                        CandidateFault(it, WriteFault.WRITE).also { injected -> fault = injected }
                    }
                ),
            )
            .use { vault ->
                val pcm = SyntheticPreservationFixture.pcm(fixture.blocks)
                try {
                    assertTrue(
                        vault.writer.append(fixture.unit(fixture.blocks), AudioFormat.PCM, pcm)
                            is AudioResult.Failed
                    )
                } finally {
                    pcm.fill(0)
                }
                assertTrue(fault.fired)
            }
        fixture.unchanged {
            val copy = fixture.acquire()
            assertThrows(IllegalStateException::class.java) { fixture.inspect(copy) }
            fixture.assertPcmPrefix(copy)
            fixture.database(copy.context) { db ->
                assertEquals(
                    1L,
                    fixture.scalar(db, "SELECT count(*) FROM unit_claim WHERE committed=0"),
                )
                assertEquals(
                    1L,
                    fixture.scalar(db, "SELECT count(*) FROM audio_intent WHERE applied=0"),
                )
            }
        }
    }

    @Test
    fun expectedCountsAreNotInferredFromReadablePrefix() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        fixture.unchanged {
            for (count in listOf(fixture.blocks - 1, fixture.blocks + 1)) {
                val copy = fixture.acquire("wrong-count-$count")
                assertThrows(IllegalStateException::class.java) { fixture.inspect(copy, count) }
            }
        }
    }

    @Test
    fun liveQuiescentWalCopyAuthenticatesAndMissingWalCannotMasqueradeAsComplete() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        // Closed preparation is the stale DB-only state. The live writer then commits solely
        // into WAL and performs no further writes/checkpoints until all preservation checks end.
        fixture.mutate("UPDATE audio_intent SET applied=0")
        fixture.database(fixture.context, false) { writer ->
            writer.query("PRAGMA journal_mode=WAL").use {
                assertTrue(it.moveToFirst())
                assertEquals("wal", it.getString(0))
            }
            writer.query("PRAGMA wal_autocheckpoint=0").use { check(it.moveToFirst()) }
            writer.execSQL("UPDATE audio_intent SET applied=1")
            assertEquals(0L, fixture.scalar(writer, "PRAGMA wal_autocheckpoint"))
            val inventory = ProtectedReadOnlyAcquisition.inventory(fixture.root)
            assertTrue(checkNotNull(inventory[fixture.databaseName + "-wal"]).bytes > 32)
            assertTrue(checkNotNull(inventory[fixture.databaseName + "-shm"]).bytes > 0)
            fixture.unchanged {
                val full = fixture.acquire("complete-wal-copy")
                assertEquals(fixture.blocks, fixture.inspect(full).authenticatedBlocks)
                fixture.assertPcmPrefix(full)
                val missing = fixture.acquire("missing-wal-copy")
                val copiedRoot = File(missing.context.noBackupFilesDir, "dora-vault-v1")
                check(File(copiedRoot, fixture.databaseName + "-wal").delete())
                check(File(copiedRoot, fixture.databaseName + "-shm").delete())
                fixture.database(missing.context) { db ->
                    assertTrue(db.isReadOnly)
                    assertEquals(
                        "DB-only copy exposes stale state",
                        0L,
                        fixture.scalar(db, "SELECT applied FROM audio_intent"),
                    )
                }
                assertThrows(IllegalStateException::class.java) { fixture.inspect(missing) }
                // The copy is intentionally damaged. Its manifest mismatch is sufficient to
                // stop; DB-only readability must never be substituted for a valid acquisition.
                missing.verifySource()
            }
        }
    }

    @Test
    fun readonlySqlCipherMayChangeCopySidecarsButNeverSourceOrCopyPayloads() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        fixture.unchanged {
            val copy = fixture.acquire()
            val copiedRoot = File(copy.context.noBackupFilesDir, "dora-vault-v1")
            val before = ProtectedReadOnlyAcquisition.inventory(copiedRoot)
            fixture.database(copy.context) { db ->
                assertTrue(db.isReadOnly)
                assertEquals(
                    fixture.blocks.toLong(),
                    fixture.scalar(db, "SELECT count(*) FROM unit_claim WHERE committed=1"),
                )
                assertThrows(Exception::class.java) {
                    db.execSQL("UPDATE audio_asset SET finalized=1")
                }
            }
            val after = ProtectedReadOnlyAcquisition.inventory(copiedRoot)
            // OPEN_READONLY is a connection property, not a promise of byte-identical SHM or
            // absent WAL sidecars. Only the isolated copy may incur these native side effects.
            fun payloads(files: Map<String, ProtectedReadOnlyAcquisition.Artifact>) =
                files.filterKeys {
                    it != fixture.databaseName + "-shm" && it != fixture.databaseName + "-wal"
                }
            assertEquals("COPY payload changed", payloads(before), payloads(after))
            before[fixture.databaseName + "-wal"]?.let {
                assertEquals(it, after[fixture.databaseName + "-wal"])
            }
        }
    }

    @Test
    fun missingRunKeyFailsWithoutReplacementOrFileMutation() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        val binding =
            "DORA/recovery-physical-alias/v1/${fixture.secrets.vaultId}/${fixture.units.last().unitId}"
        val alias =
            "dora.vault.run.v1." +
                MessageDigest.getInstance("SHA-256").digest(binding.toByteArray()).joinToString(
                    ""
                ) {
                    "%02x".format(it)
                }
        AndroidVaultKeystoreIo.remove(
            alias
        ) // Synthetic fixture damage, before the evidence boundary.
        fixture.unchanged {
            assertThrows(IllegalStateException::class.java) { fixture.inspect(fixture.acquire()) }
            assertFalse(AndroidVaultKeystoreIo.exists(alias))
        }
    }

    @Test
    fun wrongDatabaseKeyFailsOnCopyWithoutChangingSourceOrAliases() {
        val fixture = SyntheticPreservationFixture()
        fixture.prepare()
        fixture.unchanged {
            val copy = fixture.acquire()
            val wrongKey = ByteArray(32) { 0x5a.toByte() }
            try {
                assertThrows(Exception::class.java) {
                    SQLiteDatabase.openDatabase(
                            File(
                                    copy.context.noBackupFilesDir,
                                    "dora-vault-v1/${fixture.databaseName}",
                                )
                                .path,
                            wrongKey,
                            null,
                            SQLiteDatabase.OPEN_READONLY,
                            null,
                        )
                        .use {
                            it.query("SELECT count(*) FROM sqlite_master").use { row ->
                                row.moveToFirst()
                            }
                        }
                }
            } finally {
                wrongKey.fill(0)
            }
        }
    }

    @Test
    fun incompatibleSchemaAndChangedOrAbsentCiphertextStopWithoutRepair() {
        val schema = SyntheticPreservationFixture()
        schema.prepare()
        schema.mutate("PRAGMA user_version=2")
        schema.unchanged {
            assertThrows(IllegalStateException::class.java) { schema.inspect(schema.acquire()) }
        }
        val ciphertext = SyntheticPreservationFixture()
        ciphertext.prepare()
        val file =
            ciphertext.real.run(ciphertext.units.last().unitId).walkTopDown().single {
                it.isFile && it.parentFile?.name == "units" && !it.name.endsWith(".key")
            }
        val original = file.readBytes()
        file.writeBytes(
            original.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        )
        ciphertext.unchanged {
            assertThrows(IllegalStateException::class.java) {
                ciphertext.inspect(ciphertext.acquire("changed"))
            }
        }
        check(file.delete())
        ciphertext.unchanged {
            assertThrows(IllegalStateException::class.java) {
                ciphertext.inspect(ciphertext.acquire("absent"))
            }
        }
    }
}
