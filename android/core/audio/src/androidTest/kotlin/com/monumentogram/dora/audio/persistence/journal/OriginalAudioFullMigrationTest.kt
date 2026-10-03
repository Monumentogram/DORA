package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.persistence.EncryptedAudioVault
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.id
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.OriginalAudioRuntimeTest.Companion.reference
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalAudioFullMigrationTest {
    @Test
    fun finalizedAudioRecoveryQuarantineAndPendingDeletionSurviveExactV1Migration() =
        assertMigration(1)

    @Test
    fun finalizedAudioRecoveryQuarantineAndPendingDeletionSurviveExactV2Migration() =
        assertMigration(2)

    @Suppress(
        "LongMethod"
    ) // Full real encrypted source and pending deletion survive one migration.
    private fun assertMigration(oldVersion: Int) {
        val f = EncryptedAudioVaultFaultFixture()
        val deleting = f.audio.copy(recordingId = RecordingId(id()), assetId = AudioAssetId(id()))
        val ref =
            f.open().use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                success(vault.writer.finalize(f.audio))
                reference(vault, f.audio)
            }
        val encrypted = File(f.run(), RecoveryRelativeNames.microfileCiphertext(0UL)).readBytes()
        File(f.run(), RecoveryRelativeNames.microfileCiphertext(1UL)).writeBytes(encrypted)
        val dependencies =
            EncryptedAudioVault.Dependencies(
                deletionStep = { if (it == "TOMBSTONE") error("Synthetic deletion interruption") }
            )
        f.open(create = false, dependencies = dependencies).use { vault ->
            assertEquals(
                AudioResult.Failed(com.monumentogram.dora.audio.AudioFailure.INCOMPLETE),
                vault.originals.inspect(ref),
            )
            repeat(2) { success(vault.writer.reconcile(f.audio)) }
            success(vault.writer.create(deleting))
            success(
                vault.writer.append(
                    AudioStorageUnitIdentity(deleting, id(), 0, 0),
                    AudioFormat.PCM,
                    f.pcm,
                )
            )
            assertTrue(vault.deleteAudio(deleting) is AudioResult.Failed)
        }
        lateinit var before: Map<String, List<List<String?>>>
        raw(f, 3) { db ->
            before = snapshot(db)
            listOf(
                    "bootstrap",
                    "manifest",
                    "microfile",
                    "finalization_source",
                    "quarantine_intent",
                    "deletion_target",
                    "deletion_tombstone",
                )
                .forEach {
                    assertTrue("Nonempty migration coverage: $it", before.getValue(it).isNotEmpty())
                }
            // Test fixture only: baseline writer produced the original twelve tables unchanged.
            // Remove the additive v2 table, restore the exact exported v1 Room identity/version,
            // and verify the complete accepted v1 DDL before allowing the product to open it.
            db.execSQL("DROP TABLE audio_segmentation")
            if (oldVersion == 1) db.execSQL("DROP TABLE original_audio_reference")
            val roomIdentity =
                if (oldVersion == 1) "d94287707e09654e0d36efae561f369b"
                else "7665fb9df322bfee52df0e1b1f163a73"
            db.execSQL("UPDATE room_master_table SET identity_hash='$roomIdentity' WHERE id=42")
            db.execSQL("PRAGMA user_version=$oldVersion")
            JournalSchemaVerifier.verify(db, oldVersion)
        }
        f.open(create = false).close()
        raw(f, 3) { db ->
            assertTrue(
                "Every existing encrypted row must survive migration",
                before == snapshot(db),
            )
            JournalSchemaVerifier.verify(db)
        }
        f.open(create = false).use { vault ->
            assertEquals(ref, reference(vault, f.audio))
            assertTrue(
                (vault.sourceState(deleting) as AudioResult.Value).value
                    is AudioSourceState.Deleting
            )
            success(vault.retryDeletion(deleting))
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                vault.originals.acquire(deleting),
            )
            f.exact(vault, finalized = true)
        }
        f.scan()
    }

    private fun raw(
        f: EncryptedAudioVaultFaultFixture,
        version: Int,
        action: (SupportSQLiteDatabase) -> Unit,
    ) {
        val storage = AndroidVaultBundleStorage(f.context)
        val secrets =
            (VaultSecretStore(storage, AndroidVaultKeyBackend(f.context)).openExisting()
                    as KeyAccess.Available)
                .value
        val file = File(storage.vaultDirectory, "journal-${secrets.databaseObjectSelector}.db")
        secrets.borrowDatabaseSecret { key ->
            val factory = SqlCipherJournalHelperFactory(f.context, file, key)
            val helper =
                factory.create(
                    SupportSQLiteOpenHelper.Configuration.builder(f.context)
                        .name(file.path)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(version) {
                                override fun onCreate(db: SupportSQLiteDatabase) =
                                    error("Existing fixture required")

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = error("Fixture cannot upgrade")
                            }
                        )
                        .build()
                )
            try {
                action(helper.writableDatabase)
            } finally {
                helper.close()
                factory.close()
            }
        }
    }

    private fun snapshot(db: SupportSQLiteDatabase) =
        listOf(
                "vault_binding",
                "audio_asset",
                "physical_source",
                "unit_claim",
                "audio_intent",
                "bootstrap",
                "manifest",
                "microfile",
                "finalization_source",
                "quarantine_intent",
                "deletion_tombstone",
                "deletion_target",
            )
            .associateWith { table ->
                buildList {
                    db.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                        while (cursor.moveToNext()) add(
                            (0 until cursor.columnCount).map {
                                if (cursor.isNull(it)) null else cursor.getString(it)
                            }
                        )
                    }
                }
            }
}
