package com.monumentogram.dora.audio.diagnostic

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.persistence.DiagnosticJournalFactory
import com.monumentogram.dora.audio.persistence.DiagnosticSourcePolicy
import com.monumentogram.dora.audio.persistence.EncryptedAudioVault
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase
import com.monumentogram.dora.audio.persistence.journal.RoomAudioJournal
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectedHistoricalVaultTest {
    private fun id() = UUID.randomUUID().toString()

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    @Test
    fun missingDatabaseCannotReachDelegateCreation() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val missing = File(fixture.context.noBackupFilesDir, "missing.db")
        var reached = false
        val factory =
            DiagnosticJournalFactory(
                SupportSQLiteOpenHelper.Factory {
                    reached = true
                    error("Delegate must not create a database")
                },
                DiagnosticSourcePolicy.protected((1..47).map { identity() }.toSet(), emptySet()),
                id(),
                id(),
            )
        val callback =
            object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase): Unit = error("Unexpected create")

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ): Unit = error("Unexpected migration")
            }
        assertThrows(Exception::class.java) {
            factory.create(
                SupportSQLiteOpenHelper.Configuration.builder(fixture.context)
                    .name(missing.canonicalPath)
                    .callback(callback)
                    .build()
            )
        }
        assertFalse(reached)
        assertFalse(missing.exists())
    }

    @Test
    fun missingRoomMetadataCannotReachRepairCallback() {
        rejectBeforeRoomCallback("DROP TABLE room_master_table")
    }

    @Test
    fun missingBindingCannotReachBootstrapCallback() {
        rejectBeforeRoomCallback("DELETE FROM vault_binding")
    }

    @Test
    fun missingRoomIdentityCannotReachRepairCallback() {
        rejectBeforeRoomCallback("DELETE FROM room_master_table")
    }

    @Test
    fun changedRoomIdentityCannotReachRepairCallback() {
        rejectBeforeRoomCallback("UPDATE room_master_table SET identity_hash='unexpected'")
    }

    @Test
    fun olderSchemaCannotReachMigrationCallback() {
        rejectBeforeRoomCallback("PRAGMA user_version=2")
    }

    private fun rejectBeforeRoomCallback(mutation: String) {
        val fixture = EncryptedAudioVaultFaultFixture()
        val sources = (0..46).map { identity() }.toSet()
        fixture.open().use { vault ->
            if (mutation != "DELETE FROM vault_binding")
                sources.forEach { success(vault.writer.createLogicalRecording(it)) }
            val db = database(vault).openHelper.writableDatabase
            val binding =
                db.query("SELECT ownerId,vaultId FROM vault_binding").use {
                    check(it.moveToFirst())
                    it.getString(0) to it.getString(1)
                }
            lateinit var intercepted: SupportSQLiteOpenHelper.Configuration
            var callbackReached = false
            val factory =
                DiagnosticJournalFactory(
                    SupportSQLiteOpenHelper.Factory {
                        intercepted = it
                        error("Configuration captured")
                    },
                    DiagnosticSourcePolicy.protected(sources, emptySet()),
                    binding.first,
                    binding.second,
                )
            val callback =
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        callbackReached = true
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        callbackReached = true
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        callbackReached = true
                    }
                }
            assertThrows(IllegalStateException::class.java) {
                factory.create(
                    SupportSQLiteOpenHelper.Configuration.builder(fixture.context)
                        .name(db.path)
                        .callback(callback)
                        .build()
                )
            }
            db.execSQL(mutation)
            assertThrows(Exception::class.java) { intercepted.callback.onConfigure(db) }
            assertFalse("Room repair/migration must not run", callbackReached)
        }
    }

    @Test
    fun protectedSourcesRejectMutationWhileNewSourceRetainsRecoveryAndDeletion() = protectedSet(47)

    @Test
    fun successor48RejectsMutationWhileNewSourceRetainsRecoveryAndDeletion() = protectedSet(48)

    @Suppress(
        "LongMethod",
        "CyclomaticComplexMethod",
    ) // One before/after preservation proof spans all guarded routes.
    private fun protectedSet(count: Int) {
        val fixture = EncryptedAudioVaultFaultFixture()
        val historical = (listOf(fixture.audio) + (1 until count).map { identity() }).toSet()
        fixture.open().use { vault ->
            historical.forEach { success(vault.writer.createLogicalRecording(it)) }
            success(fixture.append(vault))
        }
        val policy = DiagnosticSourcePolicy.protected(historical, setOf(fixture.unit.unitId))
        val dependencies = EncryptedAudioVault.Dependencies(diagnosticPolicy = { policy })
        val fresh = identity()
        val beforeFiles = files(fixture)
        val historicalIds =
            historical
                .flatMap { listOf(it.recordingId.value, it.assetId.value, it.sessionId) }
                .toSet() + fixture.unit.unitId
        lateinit var beforeRows: Map<String, Set<List<String?>>>
        fixture.open(false, dependencies).use { vault ->
            beforeRows = rows(vault, historicalIds)
            historical.forEach { old ->
                assertTrue(vault.writer.createLogicalRecording(old) is AudioResult.Failed)
                assertTrue(vault.writer.finalize(old) is AudioResult.Failed)
                assertTrue(vault.writer.reconcile(old) is AudioResult.Failed)
                assertTrue(vault.deleteAudio(old) is AudioResult.Failed)
                assertTrue(vault.retryDeletion(old) is AudioResult.Failed)
                assertTrue(
                    vault.writer.append(
                        AudioStorageUnitIdentity(old, id(), 0, 0),
                        AudioFormat.PCM,
                        ByteArray(320),
                    ) is AudioResult.Failed
                )
                val chunk = id()
                assertTrue(
                    vault.writer.segmentation(
                        old,
                        SegmentationMetadata(
                            SegmentationKind.TECHNICAL_OPEN,
                            chunk,
                            0,
                            0,
                            captureEpochId = chunk,
                            reason = "START",
                        ),
                    ) is AudioResult.Failed
                )
                val journal = journal(vault)
                checkNotNull(journal.catalog.tryAcquire(old)).use {
                    assertThrows(Exception::class.java) {
                        journal.bootstrapJournal.beginNonExclusive()
                    }
                    assertThrows(Exception::class.java) {
                        journal.microfileJournal.beginNonExclusive()
                    }
                    assertThrows(Exception::class.java) {
                        journal.quarantineJournal.beginNonExclusive()
                    }
                    assertFalse(database(vault).inTransaction())
                }
                assertTrue(vault.originals.acquire(old) is AudioResult.Failed)
                assertTrue(vault.sourceState(old) is AudioResult.Failed)
                assertTrue(
                    vault.reader.extract(old) { _, _ -> error("Protected PCM delivered") }
                        is AudioResult.Failed
                )
                val recovery = vault.recordingRecovery(old)
                assertTrue(recovery is AudioResult.Value && !recovery.value.canResume)
            }
            var cursor = ""
            val discovered = mutableSetOf<AudioIdentity>()
            do {
                val page = (vault.recordingRecoveryPage(cursor) as AudioResult.Value).value
                page.forEach {
                    assertTrue(!it.canResume)
                    it.identity?.let(discovered::add)
                }
                if (page.isEmpty()) break
                cursor = page.last().cursor
            } while (true)
            assertEquals(historical, discovered)
            assertEquals(beforeRows, rows(vault, historicalIds))
            success(vault.writer.createLogicalRecording(fresh))
            assertTrue(
                vault.writer.append(
                    AudioStorageUnitIdentity(fresh, fixture.unit.unitId, 0, 0),
                    AudioFormat.PCM,
                    ByteArray(320),
                ) is AudioResult.Failed
            )
            val chunk = id()
            success(
                vault.writer.segmentation(
                    fresh,
                    SegmentationMetadata(
                        SegmentationKind.TECHNICAL_OPEN,
                        chunk,
                        0,
                        0,
                        captureEpochId = chunk,
                        reason = "START",
                    ),
                )
            )
            success(
                vault.writer.append(
                    AudioStorageUnitIdentity(fresh, id(), 0, 0, chunk, 0, 0),
                    AudioFormat.PCM,
                    ByteArray(320),
                )
            )
        }
        fixture.open(false, dependencies).use { vault ->
            val recovered = vault.recordingRecovery(fresh)
            assertTrue(recovered is AudioResult.Value && recovered.value.canResume)
            success(vault.writer.finalize(fresh))
            val read = vault.reader.extract(fresh) { _, _ -> }
            assertTrue(read is AudioResult.Value)
            assertEquals(160L, (read as AudioResult.Value).value.frames)
            success(vault.deleteAudio(fresh))
            assertEquals(beforeRows, rows(vault, historicalIds))
        }
        val afterFiles = files(fixture)
        beforeFiles.forEach { (name, hash) ->
            assertEquals("Historical file changed", hash, afterFiles[name])
        }
        // Original source is still unfinalized and authenticated after ordinary reopen.
        fixture.open(false).use { fixture.exact(it) }
    }

    private fun success(value: AudioResult<*>) = assertTrue(value is AudioResult.Value)

    private fun files(fixture: EncryptedAudioVaultFaultFixture): Map<String, String> =
        fixture.context.noBackupFilesDir
            .walkTopDown()
            .filter { it.isFile && !it.name.startsWith("journal-") }
            .associate {
                it.relativeTo(fixture.context.noBackupFilesDir).path to
                    MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") {
                        byte ->
                        "%02x".format(byte)
                    }
            }

    private fun rows(
        vault: EncryptedAudioVault,
        historicalIds: Set<String>,
    ): Map<String, Set<List<String?>>> {
        val db = database(vault).openHelper.readableDatabase
        val tables =
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
                "original_audio_reference",
                "audio_segmentation",
            )
        return tables.associateWith { table ->
            db.query("SELECT * FROM $table").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        val row = (0 until cursor.columnCount).map { cursor.getString(it) }
                        if (table == "vault_binding" || row.any { it in historicalIds }) add(row)
                    }
                }
            }
        }
    }

    private fun database(vault: EncryptedAudioVault): AudioJournalDatabase {
        return RoomAudioJournal::class
            .java
            .getDeclaredField("database")
            .apply { isAccessible = true }
            .get(journal(vault)) as AudioJournalDatabase
    }

    private fun journal(vault: EncryptedAudioVault): RoomAudioJournal =
        EncryptedAudioVault::class
            .java
            .getDeclaredField("journal")
            .apply { isAccessible = true }
            .get(vault) as RoomAudioJournal
}
