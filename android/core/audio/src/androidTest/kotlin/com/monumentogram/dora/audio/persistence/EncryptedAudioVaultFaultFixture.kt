package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class EncryptedAudioVaultFaultFixture {
    val audio = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
    val pcm = ByteArray(320) { (it * 37 + 19).toByte() }
    val unit = AudioStorageUnitIdentity(audio, id(), 0, 0)
    val context: Context

    init {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.noBackupFilesDir, "fault-${id()}").apply { check(mkdir()) }
        context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
    }

    fun open(
        create: Boolean = true,
        dependencies: EncryptedAudioVault.Dependencies = EncryptedAudioVault.Dependencies(),
    ): EncryptedAudioVault {
        val result = EncryptedAudioVault.open(context, create, {}, { it() }, dependencies)
        assertTrue("Real encrypted vault must open", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    fun append(vault: EncryptedAudioVault, selected: AudioStorageUnitIdentity = unit) =
        vault.writer.append(selected, AudioFormat.PCM, pcm)

    fun exact(vault: EncryptedAudioVault, units: Int = 1, finalized: Boolean = false) {
        val frames = mutableListOf<Long>()
        val borrowed = mutableListOf<ByteArray>()
        val result =
            vault.reader.extract(audio) { frame, bytes ->
                assertTrue("Exact synthetic PCM required", pcm.contentEquals(bytes))
                frames += frame
                borrowed += bytes
            }
        assertTrue("Authenticated source required", result is AudioResult.Value)
        val summary = (result as AudioResult.Value).value
        assertEquals(units * 160L, summary.frames)
        assertEquals((0 until units).map { it * 160L }, frames)
        assertEquals(
            if (finalized) AudioCompletion.FINALIZED else AudioCompletion.PARTIAL_RECOVERED,
            summary.completion,
        )
        assertTrue(borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    fun scan() {
        val secrets =
            (VaultSecretStore(AndroidVaultBundleStorage(context), AndroidVaultKeyBackend(context))
                    .openExisting() as KeyAccess.Available)
                .value
        val digest = MessageDigest.getInstance("SHA-256").digest(pcm)
        val canaries =
            listOf(
                pcm,
                secrets.ownerId.toByteArray(),
                secrets.vaultId.toByteArray(),
                digest,
                digest.joinToString("") { "%02x".format(it) }.toByteArray(),
                audio.assetId.value.toByteArray(),
                audio.recordingId.value.toByteArray(),
                audio.sessionId.toByteArray(),
            )
        context.noBackupFilesDir
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val bytes = file.readBytes()
                canaries.forEach { canary ->
                    assertFalse("Plaintext canary in vault artifact", bytes.containsSlice(canary))
                }
            }
    }

    fun run(unitId: String = unit.unitId) =
        File(context.noBackupFilesDir, "dora-vault-v1/poc-recovery/v1/runs/$unitId")

    companion object {
        fun id() = UUID.randomUUID().toString()

        fun success(result: AudioResult<*>) =
            assertTrue("Persistence operation must succeed", result is AudioResult.Value)

        private fun ByteArray.containsSlice(needle: ByteArray) =
            size >= needle.size &&
                (0..size - needle.size).any { start ->
                    needle.indices.all { this[start + it] == needle[it] }
                }
    }
}
