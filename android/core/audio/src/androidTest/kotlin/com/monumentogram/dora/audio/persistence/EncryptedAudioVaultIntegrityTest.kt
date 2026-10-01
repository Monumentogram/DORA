package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.audio.persistence.keys.VaultKeystoreIo
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedAudioVaultIntegrityTest {
    @Test
    fun missingMiddleAndTailNeverSkipLogicalHole() {
        for (missing in listOf(1, 2)) {
            val fixture = EncryptedAudioVaultFaultFixture()
            val units =
                (0..2).map {
                    fixture.unit.copy(
                        unitId = EncryptedAudioVaultFaultFixture.id(),
                        ordinal = it,
                        firstFrame = it * 160L,
                        sourceFrameOffset = it * 160L,
                    )
                }
            fixture.open().use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                units.forEach { EncryptedAudioVaultFaultFixture.success(fixture.append(vault, it)) }
                EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(fixture.audio))
            }
            assertTrue(
                File(
                        fixture.run(units[missing].unitId),
                        RecoveryRelativeNames.microfileCiphertext(0UL),
                    )
                    .delete()
            )
            fixture.open(false).use { vault ->
                val delivered = mutableListOf<Long>()
                val result =
                    vault.reader.extract(fixture.audio) { frame, bytes ->
                        delivered += frame
                        assertTrue(fixture.pcm.contentEquals(bytes))
                    }
                assertTrue(result is AudioResult.Value)
                val summary = (result as AudioResult.Value).value
                assertEquals(AudioCompletion.PARTIAL_RECOVERED, summary.completion)
                assertEquals(AudioFailure.INCOMPLETE, summary.tailFailure)
                assertEquals(missing * 160L, summary.frames)
                assertEquals((0 until missing).map { it * 160L }, delivered)
                assertEquals(
                    AudioResult.Failed(AudioFailure.COLLISION),
                    vault.writer.finalize(fixture.audio),
                )
                fixture.scan()
            }
        }
    }

    @Test
    fun laterCiphertextCorruptionRejectsEntireAttemptAndWipesBorrowedPrefix() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val next =
            fixture.unit.copy(
                unitId = EncryptedAudioVaultFaultFixture.id(),
                ordinal = 1,
                firstFrame = 160,
                sourceFrameOffset = 160,
            )
        fixture.open().use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault, next))
            EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(fixture.audio))
        }
        val ciphertext =
            File(fixture.run(next.unitId), RecoveryRelativeNames.microfileCiphertext(0UL))
        val bytes = ciphertext.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        ciphertext.writeBytes(bytes)
        fixture.open(false).use { vault ->
            val borrowed = mutableListOf<ByteArray>()
            val result = vault.reader.extract(fixture.audio) { _, pcm -> borrowed += pcm }
            assertEquals(AudioResult.Failed(AudioFailure.CORRUPT), result)
            assertEquals(1, borrowed.size)
            assertTrue(borrowed.single().all { it == 0.toByte() })
            fixture.scan()
        }
    }

    @Test
    fun finalizedReplayAndCrossAssetNamespaceStayImmutableAfterReopen() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
            EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(fixture.audio))
        }
        val before =
            fixture
                .run()
                .walkTopDown()
                .filter { it.isFile }
                .associate { it.name to it.readBytes().toList() }
        fixture.open(false).use { vault ->
            assertEquals(AudioResult.Failed(AudioFailure.COLLISION), fixture.append(vault))
            assertEquals(
                AudioResult.Failed(AudioFailure.COLLISION),
                vault.writer.append(fixture.unit, AudioFormat.PCM, ByteArray(160)),
            )
            assertEquals(
                AudioResult.Failed(AudioFailure.COLLISION),
                vault.writer.finalize(fixture.audio),
            )
            val other =
                fixture.audio.copy(
                    assetId =
                        com.monumentogram.dora.model.alpha.AudioAssetId(
                            EncryptedAudioVaultFaultFixture.id()
                        )
                )
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(other))
            assertEquals(
                AudioResult.Failed(AudioFailure.COLLISION),
                fixture.append(vault, fixture.unit.copy(audio = other)),
            )
            repeat(2) {
                EncryptedAudioVaultFaultFixture.success(vault.writer.reconcile(fixture.audio))
            }
            fixture.exact(vault, finalized = true)
            fixture.scan()
        }
        assertTrue(
            "Immutable encrypted artifacts",
            before ==
                fixture
                    .run()
                    .walkTopDown()
                    .filter { it.isFile }
                    .associate { it.name to it.readBytes().toList() },
        )
    }

    @Test
    fun encryptedDatabasePageDamageRetainsCiphertextAndRejectsReplacement() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
        }
        val files = fixture.context.noBackupFilesDir.walkTopDown().filter { it.isFile }.toList()
        val database = files.single { it.name.endsWith(".db") }
        val retained =
            files.filter { it != database }.associate { it.path to it.readBytes().toList() }
        RandomAccessFile(database, "rw").use { file ->
            file.seek(128)
            val value = file.readByte()
            file.seek(128)
            file.writeByte(value.toInt() xor 1)
        }
        val damaged = database.readBytes()
        assertEquals(
            AudioResult.Failed(AudioFailure.UNAVAILABLE),
            EncryptedAudioVault.openExisting(fixture.context) {},
        )
        assertTrue(EncryptedAudioVault.createNew(fixture.context) {} is AudioResult.Failed)
        assertTrue(damaged.contentEquals(database.readBytes()))
        retained.forEach { (path, bytes) ->
            assertTrue("Retained encrypted artifact", bytes == File(path).readBytes().toList())
        }
        fixture.scan()
    }

    @Test
    fun actualMissingRunKeyRetainsArtifactsAndCannotFinalizeOrReplace() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
        }
        val secrets =
            (VaultSecretStore(
                        AndroidVaultBundleStorage(fixture.context),
                        AndroidVaultKeyBackend(fixture.context),
                    )
                    .openExisting() as KeyAccess.Available)
                .value
        val keys = NoLogRecoveryRunAeadBackend(fixture.context, secrets.vaultId)
        val run = RunId.fromCanonicalString(fixture.unit.unitId)
        keys.removeAlias(run)
        val retained =
            fixture
                .run()
                .walkTopDown()
                .filter { it.isFile }
                .associate { it.name to it.readBytes().toList() }
        fixture.open(false).use { vault ->
            assertEquals(
                AudioResult.Failed(AudioFailure.KEY_INVALIDATED),
                vault.reader.extract(fixture.audio) { _, _ -> error("Missing key delivered PCM") },
            )
            assertEquals(
                AudioResult.Failed(AudioFailure.KEY_INVALIDATED),
                vault.writer.finalize(fixture.audio),
            )
            assertEquals(AudioResult.Failed(AudioFailure.COLLISION), fixture.append(vault))
            assertFalse(keys.aliasExists(run))
            fixture.scan()
        }
        assertTrue(
            "Retained encrypted artifacts",
            retained ==
                fixture
                    .run()
                    .walkTopDown()
                    .filter { it.isFile }
                    .associate { it.name to it.readBytes().toList() },
        )
    }

    @Test
    fun operationalDatabaseFailureIsSanitizedAndExactSourceSurvivesReopen() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val probe = DatabaseProbe()
        fixture
            .open(dependencies = EncryptedAudioVault.Dependencies(helperFactory = probe::factory))
            .use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                probe.failQueries = true
                val result =
                    vault.reader.extract(fixture.audio) { _, _ ->
                        error("Unavailable DB delivered")
                    }
                assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), result)
                assertFalse(result.toString().contains("SYNTHETIC_DB_PATH_CANARY"))
                assertEquals(
                    AudioResult.Failed(AudioFailure.UNCERTAIN),
                    vault.writer.finalize(fixture.audio),
                )
                probe.failQueries = false
                fixture.scan()
            }
        fixture.open(false).use { vault ->
            fixture.exact(vault)
            fixture.scan()
        }
    }

    @Test
    fun staleIntentOrOrphanMappingOrChangedSourceIsRejectedAfterReopen() {
        for (mutation in
            listOf(
                "UPDATE audio_intent SET applied=0",
                "DELETE FROM microfile",
                "UPDATE unit_claim SET frames=frames+1",
            )) {
            val fixture = EncryptedAudioVaultFaultFixture()
            val probe = DatabaseProbe()
            fixture
                .open(
                    dependencies = EncryptedAudioVault.Dependencies(helperFactory = probe::factory)
                )
                .use { vault ->
                    EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                    EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                    probe.database.execSQL(mutation)
                    assertEquals(
                        AudioResult.Failed(AudioFailure.UNCERTAIN),
                        vault.reader.extract(fixture.audio) { _, _ ->
                            error("Invalid mapping delivered")
                        },
                    )
                }
            fixture.open(false).use { vault ->
                repeat(2) {
                    assertEquals(
                        AudioResult.Failed(AudioFailure.UNCERTAIN),
                        vault.writer.reconcile(fixture.audio),
                    )
                }
                assertEquals(
                    AudioResult.Failed(AudioFailure.UNCERTAIN),
                    vault.reader.extract(fixture.audio) { _, _ ->
                        error("Orphan mapping delivered")
                    },
                )
                fixture.scan()
            }
        }
    }

    @Test
    fun orphanEncryptedArtifactCannotJoinTheAuthenticatedSource() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
        }
        val ciphertext =
            File(fixture.run(), RecoveryRelativeNames.microfileCiphertext(0UL)).readBytes()
        val orphan = File(fixture.run(), RecoveryRelativeNames.microfileCiphertext(1UL))
        orphan.writeBytes(ciphertext)
        fixture.open(false).use { vault ->
            repeat(2) {
                EncryptedAudioVaultFaultFixture.success(vault.writer.reconcile(fixture.audio))
            }
            fixture.exact(vault)
            val retained =
                fixture.context.noBackupFilesDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .any { it.readBytes().contentEquals(ciphertext) }
            assertTrue("Encrypted source retained", retained)
            fixture.scan()
        }
    }

    @Test
    fun actualRunKeyProviderUnavailableUsesTypedFailureAndRecoversWithoutReplacement() {
        val fixture = EncryptedAudioVaultFaultFixture()
        var fail = false
        var generated = 0
        val dependencies =
            EncryptedAudioVault.Dependencies(
                runKeystore = { real ->
                    object : VaultKeystoreIo by real {
                        override fun open(alias: String): javax.crypto.SecretKey? {
                            if (fail)
                                throw java.security.ProviderException("SYNTHETIC_PROVIDER_CANARY")
                            return real.open(alias)
                        }

                        override fun generate(alias: String, strongBox: Boolean) {
                            generated++
                            real.generate(alias, strongBox)
                        }
                    }
                }
            )
        fixture.open(dependencies = dependencies).use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
            val originalGenerationCount = generated
            fail = true
            val result =
                vault.reader.extract(fixture.audio) { _, _ -> error("Unavailable key delivered") }
            assertEquals(AudioResult.Failed(AudioFailure.KEY_UNAVAILABLE), result)
            assertFalse(result.toString().contains("SYNTHETIC_PROVIDER_CANARY"))
            assertEquals(
                AudioResult.Failed(AudioFailure.KEY_UNAVAILABLE),
                vault.writer.finalize(fixture.audio),
            )
            assertEquals(originalGenerationCount, generated)
            fail = false
            fixture.scan()
        }
        fixture.open(false, dependencies).use { vault ->
            val originalGenerationCount = generated
            repeat(2) {
                EncryptedAudioVaultFaultFixture.success(vault.writer.reconcile(fixture.audio))
            }
            fixture.exact(vault, finalized = true)
            assertEquals(originalGenerationCount, generated)
            fixture.scan()
        }
    }
}
