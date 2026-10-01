package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedAudioVaultTest {
    @Test
    fun sourceStatusAndRetryNeverInventADeletion() {
        val context = isolatedContext()
        val audio = identity()
        opened(EncryptedAudioVault.createNew(context) {}).use { vault ->
            assertEquals(
                AudioResult.Value(com.monumentogram.dora.audio.AudioSourceState.Missing),
                vault.sourceState(audio),
            )
            success(vault.writer.create(audio))
            success(
                vault.writer.append(
                    AudioStorageUnitIdentity(audio, id(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32),
                )
            )
            assertEquals(
                AudioResult.Failed(com.monumentogram.dora.audio.AudioFailure.INVALID_INPUT),
                vault.retryDeletion(audio),
            )
            assertTrue(vault.reader.extract(audio) { _, _ -> } is AudioResult.Value)
            success(vault.deleteAudio(audio))
            assertEquals(
                AudioResult.Value(com.monumentogram.dora.audio.AudioSourceState.UserDeleted),
                vault.sourceState(audio),
            )
            assertTrue(vault.sourceState(audio.copy(sessionId = id())) is AudioResult.Failed)
        }
        opened(EncryptedAudioVault.openExisting(context) {}).use { vault ->
            assertEquals(
                AudioResult.Value(com.monumentogram.dora.audio.AudioSourceState.UserDeleted),
                vault.sourceState(audio),
            )
            success(vault.retryDeletion(audio))
        }
    }

    @Test
    fun deletionAfterKeyLossCannotEraseASecondVaultUsingTheSameLogicalRun() {
        val ownerContext = isolatedContext()
        val collisionContext = isolatedContext()
        val owner = identity()
        val collision = identity()
        val run = id()
        opened(EncryptedAudioVault.createNew(ownerContext) {}).use { first ->
            success(first.writer.create(owner))
            success(
                first.writer.append(
                    AudioStorageUnitIdentity(owner, run, 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32),
                )
            )
            val originalKeys = existingKeys(ownerContext)
            originalKeys.removeAlias(RunId.fromCanonicalString(run))
            opened(EncryptedAudioVault.createNew(collisionContext) {}).use { second ->
                success(second.writer.create(collision))
                success(
                    second.writer.append(
                        AudioStorageUnitIdentity(collision, run, 0, 0),
                        AudioFormat.PCM,
                        ByteArray(32),
                    )
                )
                success(first.deleteAudio(owner))
                assertTrue(
                    "Deleting the key-lost original cannot erase another vault's key",
                    second.reader.extract(collision) { _, _ -> } is AudioResult.Value,
                )
            }
            opened(EncryptedAudioVault.openExisting(collisionContext) {}).use { second ->
                assertTrue(
                    "Other vault remains readable after restart",
                    second.reader.extract(collision) { _, _ -> } is AudioResult.Value,
                )
            }
        }
    }

    @Test
    fun uncommittedPreexistingAliasInTheSameVaultKeepsDeletionFenced() {
        val context = isolatedContext()
        val audio = identity()
        val run = RunId.fromCanonicalString(id())
        opened(EncryptedAudioVault.createNew(context) {}).use { vault ->
            val keys = existingKeys(context)
            keys.generateNew(CanonicalRecoveryAlias.forRun(run))
            success(vault.writer.create(audio))
            assertTrue(
                vault.writer.append(
                    AudioStorageUnitIdentity(audio, run.toCanonicalString(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32),
                ) is AudioResult.Failed
            )
            assertTrue(
                "Ambiguous existing key must keep deletion fenced",
                vault.deleteAudio(audio) is AudioResult.Failed,
            )
            assertTrue(keys.aliasExists(run))
        }
        opened(EncryptedAudioVault.openExisting(context) {}).use { vault ->
            assertTrue(
                "Restart cannot invent bootstrap ownership",
                vault.deleteAudio(audio) is AudioResult.Failed,
            )
            assertTrue(existingKeys(context).aliasExists(run))
        }
    }

    private fun existingKeys(context: Context): NoLogRecoveryRunAeadBackend {
        val result =
            VaultSecretStore(AndroidVaultBundleStorage(context), AndroidVaultKeyBackend(context))
                .openExisting()
        assertTrue(result is KeyAccess.Available)
        return NoLogRecoveryRunAeadBackend(context, (result as KeyAccess.Available).value.vaultId)
    }

    @Test
    fun explicitDeletionRemovesOnlyTheSelectedAudioAndCannotReuseItsNamespace() {
        val context = isolatedContext()
        val selected = identity()
        val preserved = identity()
        val selectedUnit = AudioStorageUnitIdentity(selected, id(), 0, 0)
        val keptUnit = AudioStorageUnitIdentity(preserved, id(), 0, 0)
        opened(EncryptedAudioVault.createNew(context) {}).use { vault ->
            for ((audio, unit) in listOf(selected to selectedUnit, preserved to keptUnit)) {
                success(vault.writer.create(audio))
                success(vault.writer.append(unit, AudioFormat.PCM, ByteArray(32) { 6 }))
            }
            val run =
                File(
                    context.noBackupFilesDir,
                    "dora-vault-v1/poc-recovery/v1/runs/${selectedUnit.unitId}",
                )
            File(run, "interrupted-ciphertext.tmp").writeBytes(ByteArray(24) { 9 })
            success(vault.deleteAudio(selected))
            assertTrue("Selected run must be absent", !run.exists())
            assertTrue(
                vault.reader.extract(selected) { _, _ -> error("Deleted source delivered bytes") }
                    is AudioResult.Failed
            )
            assertTrue(
                "Other audio must remain readable",
                vault.reader.extract(preserved) { _, _ -> } is AudioResult.Value,
            )
            success(vault.deleteAudio(selected))
            assertTrue(
                "Deleted asset cannot be replaced",
                vault.writer.create(selected) is AudioResult.Failed,
            )
            val other = identity()
            success(vault.writer.create(other))
            assertTrue(
                "Deleted run namespace remains occupied",
                vault.writer.append(
                    selectedUnit.copy(audio = other),
                    AudioFormat.PCM,
                    ByteArray(32),
                ) is AudioResult.Failed,
            )
        }
        opened(EncryptedAudioVault.openExisting(context) {}).use { vault ->
            success(vault.deleteAudio(selected))
            assertTrue(vault.reader.extract(preserved) { _, _ -> } is AudioResult.Value)
        }
    }

    @Test
    fun unsafeDeletionLeafLeavesADurableFenceAndRetryAfterReopenCompletes() {
        val context = isolatedContext()
        val selected = identity()
        val unit = AudioStorageUnitIdentity(selected, id(), 0, 0)
        val outside =
            File(context.noBackupFilesDir, "outside-synthetic-marker").apply {
                writeBytes(byteArrayOf(8, 9))
            }
        val link =
            File(
                context.noBackupFilesDir,
                "dora-vault-v1/poc-recovery/v1/runs/${unit.unitId}/unsafe-link",
            )
        opened(EncryptedAudioVault.createNew(context) {}).use { vault ->
            success(vault.writer.create(selected))
            success(vault.writer.append(unit, AudioFormat.PCM, ByteArray(32)))
            Os.symlink(outside.path, link.path)
            assertTrue(vault.deleteAudio(selected) is AudioResult.Failed)
            assertTrue(
                "Failure must retain outside data",
                outside.readBytes().contentEquals(byteArrayOf(8, 9)),
            )
            assertTrue(
                "Durable delete fence blocks source access",
                vault.reader.extract(selected) { _, _ -> error("Deletion fence bypassed") }
                    is AudioResult.Failed,
            )
        }
        Os.remove(link.path)
        opened(EncryptedAudioVault.openExisting(context) {}).use { vault ->
            success(vault.deleteAudio(selected))
        }
        assertTrue(
            "Retry must preserve outside data",
            outside.readBytes().contentEquals(byteArrayOf(8, 9)),
        )
    }

    @Test
    fun finalizedSyntheticAudioSurvivesRealEncryptedReopen() {
        val context = isolatedContext()
        val identity = identity()
        val first = ByteArray(320) { (it * 29 + 7).toByte() }
        val second = ByteArray(160) { (it * 17 + 3).toByte() }
        val unit = AudioStorageUnitIdentity(identity, id(), 0, 0)
        val next = AudioStorageUnitIdentity(identity, id(), 1, 160)
        opened(EncryptedAudioVault.createNew(context) {}).use { vault ->
            success(vault.writer.create(identity))
            success(vault.writer.append(unit, AudioFormat.PCM, first))
            success(vault.writer.append(next, AudioFormat.PCM, second))
            success(vault.writer.finalize(identity))
        }
        opened(EncryptedAudioVault.openExisting(context) {}).use { vault ->
            success(vault.writer.reconcile(identity))
            val received = mutableListOf<ByteArray>()
            val frames = mutableListOf<Long>()
            val result =
                vault.reader.extract(identity) { frame, bytes ->
                    frames += frame
                    received += bytes.copyOf()
                }
            assertTrue("Authenticated extraction required", result is AudioResult.Value)
            val summary = (result as AudioResult.Value).value
            assertEquals(AudioCompletion.FINALIZED, summary.completion)
            assertEquals(240L, summary.frames)
            assertEquals(listOf(0L, 160L), frames)
            assertTrue(
                "Synthetic units must round-trip exactly",
                received[0].contentEquals(first) && received[1].contentEquals(second),
            )
            received.forEach { it.fill(0) }
        }
        context.noBackupFilesDir
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val bytes = file.readBytes()
                assertTrue(
                    "Storage must not contain plaintext synthetic audio",
                    !bytes.containsSlice(first),
                )
                assertTrue(
                    "Storage must not contain plaintext product identity",
                    !bytes.containsSlice(identity.assetId.value.toByteArray()),
                )
            }
    }

    @Test
    fun unfinalizedSourceRequiresReconciliationAndDoesNotBecomeFinalizedOnReopen() {
        val context = isolatedContext()
        val identity = identity()
        val pcm = ByteArray(32) { it.toByte() }
        opened(EncryptedAudioVault.createNew(context) {}).use { vault ->
            success(vault.writer.create(identity))
            success(
                vault.writer.append(
                    AudioStorageUnitIdentity(identity, id(), 0, 0),
                    AudioFormat.PCM,
                    pcm,
                )
            )
        }
        opened(EncryptedAudioVault.openExisting(context) {}).use { vault ->
            success(vault.writer.reconcile(identity))
            var borrowed: ByteArray? = null
            val result = vault.reader.extract(identity) { _, bytes -> borrowed = bytes }
            assertTrue(result is AudioResult.Value)
            assertEquals(
                AudioCompletion.PARTIAL_RECOVERED,
                (result as AudioResult.Value).value.completion,
            )
            assertTrue("Borrowed plaintext must be erased", borrowed!!.all { it == 0.toByte() })
        }
    }

    @Test
    fun revocationBetweenUnitsStopsFurtherPlaintextDeliveryAndCleanupRemainsPossible() {
        val context = isolatedContext()
        val identity = identity()
        val allowed = AtomicBoolean(true)
        opened(EncryptedAudioVault.createNew(context) { check(allowed.get()) }).use { vault ->
            success(vault.writer.create(identity))
            repeat(2) { index ->
                success(
                    vault.writer.append(
                        AudioStorageUnitIdentity(identity, id(), index, index * 16L),
                        AudioFormat.PCM,
                        ByteArray(32) { 5 },
                    )
                )
            }
            success(vault.writer.finalize(identity))
            var calls = 0
            val result =
                vault.reader.extract(identity) { _, _ ->
                    calls++
                    allowed.set(false)
                }
            assertEquals(1, calls)
            assertTrue(
                "Revoked session cannot return successful extraction",
                result is AudioResult.Failed,
            )
            assertTrue(vault.writer.create(identity()) is AudioResult.Failed)
        }
    }

    @Test
    fun missingExistingDatabaseCannotBeSilentlyRecreated() {
        val context = isolatedContext()
        opened(EncryptedAudioVault.createNew(context) {}).close()
        val db =
            context.noBackupFilesDir.walkTopDown().single { it.isFile && it.name.endsWith(".db") }
        assertTrue(db.delete())
        assertTrue(EncryptedAudioVault.openExisting(context) {} is AudioResult.Failed)
        assertTrue("Reopen cannot replace the missing database", !db.exists())
        assertTrue(
            "Create cannot replace an existing vault",
            EncryptedAudioVault.createNew(context) {} is AudioResult.Failed,
        )
    }

    private fun opened(result: AudioResult<EncryptedAudioVault>): EncryptedAudioVault {
        assertTrue("Real encrypted vault must open", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    private fun success(result: AudioResult<Unit>) =
        assertTrue("Persistence operation must succeed", result is AudioResult.Value)

    private fun id() = UUID.randomUUID().toString()

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    private fun isolatedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.noBackupFilesDir, "vault-composition-test-${id()}")
        check(root.mkdir())
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this

            override fun getNoBackupFilesDir(): File = root
        }
    }

    private fun ByteArray.containsSlice(needle: ByteArray): Boolean =
        size >= needle.size &&
            (0..size - needle.size).any { start ->
                needle.indices.all { this[start + it] == needle[it] }
            }
}
