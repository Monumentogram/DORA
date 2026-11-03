package com.monumentogram.dora.audio.persistence.keys

import android.os.Bundle
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioLifecycle
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.security.ProviderException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real storage, Tink and Keystore; only counts and faults the platform seam. */
class RunKeyReusePipelineTest {
    @Test
    fun rejectedCiphertextStillAuthenticatesAndRetainsEveryDependentManifestArtifact() {
        val keys = CountingKeys()
        try {
            RunKeyProductFixture(keys).use { f ->
                val audio = create(f, 1)
                val run =
                    requireNotNull(f.journal.catalog.tryAcquire(audio)).use {
                        RunId.fromCanonicalString(
                            f.journal.catalog.load(audio)!!.segments.single().identity.unitId
                        )
                    }
                val ciphertext =
                    File(
                        f.context.noBackupFilesDir,
                        "dora-vault-v1/poc-recovery/v1/runs/${run.toCanonicalString()}/units/u-0000000000.ct",
                    )
                val bytes = ciphertext.readBytes()
                bytes[0] = (bytes[0].toInt() xor 1).toByte()
                ciphertext.writeBytes(bytes)
                keys.reset()
                assertEquals(
                    AudioResult.Failed(AudioFailure.CORRUPT),
                    f.bridge.extract(audio) { _, _ -> error("Rejected source delivered PCM") },
                )
                val retained =
                    requireNotNull(f.journal.catalog.tryAcquire(audio)).use {
                        f.journal.loadAllQuarantine(run)
                    }
                assertEquals(4, retained.size)
                assertEquals(
                    setOf(
                        RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                        RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE,
                        RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT,
                        RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE,
                    ),
                    retained.map { it.input.artifactRole }.toSet(),
                )
                assertTrue(retained.all { it.state == QuarantineIntentState.COMPLETED })
                assertEquals(
                    1,
                    retained.count {
                        it.recordedObservedState ==
                            RecoveryQuarantineObservedState.REFERENCED_REJECTED
                    },
                )
                assertEquals(
                    3,
                    retained.count {
                        it.recordedObservedState ==
                            RecoveryQuarantineObservedState.REFERENCED_DEPENDENT
                    },
                )
                assertCounts(keys, 1, 5)
            }
        } finally {
            keys.aliases.forEach(AndroidVaultKeystoreIo::remove)
        }
    }

    private class CountingKeys : VaultKeystoreIo by AndroidVaultKeystoreIo {
        val aliases = mutableListOf<String>()
        var opens = 0
        var infos = 0
        var ciphers = 0
        var failCipher = 0

        override fun generate(alias: String, strongBox: Boolean) {
            AndroidVaultKeystoreIo.generate(alias, strongBox)
            aliases += alias
        }

        override fun open(alias: String): SecretKey? {
            opens++
            return AndroidVaultKeystoreIo.open(alias)
        }

        override fun information(key: SecretKey): KeyInfo {
            infos++
            return AndroidVaultKeystoreIo.information(key)
        }

        override fun cipher(): Cipher {
            ciphers++
            if (ciphers == failCipher)
                throw ProviderException("synthetic", KeyPermanentlyInvalidatedException())
            return AndroidVaultKeystoreIo.cipher()
        }

        fun reset() {
            opens = 0
            infos = 0
            ciphers = 0
            failCipher = 0
        }
    }

    @Test
    fun finalizationAndFreshReadsUseOneValidatedOpenAndFiveProviderCallsPerUnit() {
        val keys = CountingKeys()
        try {
            RunKeyProductFixture(keys).use { f ->
                val audio = create(f, 4)
                val observations = mutableListOf<Triple<Int, Int, Int>>()
                keys.reset()
                val start = System.nanoTime()
                assertEquals(AudioResult.Value(Unit), f.bridge.finalize(audio))
                receipt("FINALIZE", keys, System.nanoTime() - start)
                observations += Triple(keys.opens, keys.infos, keys.ciphers)
                val lifecycle =
                    OriginalAudioLifecycle(
                        f.ownerId,
                        f.vaultId,
                        f.journal.catalog,
                        { null },
                        { null },
                        { _, _ -> },
                        f.bridge::extractFinalizedHeld,
                        {},
                        { it() },
                        f.bridge::extractRangeHeld,
                    )
                repeat(2) {
                    keys.reset()
                    val readStart = System.nanoTime()
                    val handle = (lifecycle.openAuthenticated(audio) as AudioResult.Value).value
                    receipt("AUTHENTICATED_OPEN", keys, System.nanoTime() - readStart)
                    assertEquals(320_000L, handle.reference.frames)
                    handle.close()
                    observations += Triple(keys.opens, keys.infos, keys.ciphers)
                }
                observations.forEach { assertEquals(Triple(4, 4, 20), it) }
            }
        } finally {
            keys.aliases.forEach(AndroidVaultKeystoreIo::remove)
        }
    }

    @Test
    fun providerInvalidationAfterConfirmationStillRejectsBothReusedEnvelopeDecrypts() {
        val keys = CountingKeys()
        try {
            RunKeyProductFixture(keys).use { f ->
                val audio = create(f, 1)
                for (cipher in listOf(4, 5)) {
                    keys.reset()
                    keys.failCipher = cipher
                    assertEquals(
                        AudioResult.Failed(AudioFailure.KEY_INVALIDATED),
                        f.bridge.extract(audio) { _, _ -> error("Invalidated key delivered PCM") },
                    )
                    assertEquals(1, keys.opens)
                    keys.reset()
                    assertTrue(f.bridge.extract(audio) { _, _ -> } is AudioResult.Value)
                    assertCounts(keys, 1, 5)
                }
            }
        } finally {
            keys.aliases.forEach(AndroidVaultKeystoreIo::remove)
        }
    }

    private fun assertCounts(keys: CountingKeys, opens: Int, ciphers: Int) {
        assertEquals(opens, keys.opens)
        assertEquals(opens, keys.infos)
        assertEquals(ciphers, keys.ciphers)
    }

    private fun receipt(operation: String, keys: CountingKeys, elapsed: Long) {
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                Bundle().apply {
                    putString(
                        "stream",
                        "RUN_KEY_PIPELINE $operation units=4 opens=${keys.opens} " +
                            "infos=${keys.infos} ciphers=${keys.ciphers} elapsedNs=$elapsed\n",
                    )
                },
            )
    }

    private fun create(f: RunKeyProductFixture, count: Int): AudioIdentity {
        val audio = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
        assertEquals(AudioResult.Value(Unit), f.bridge.create(audio))
        repeat(count) { index ->
            assertEquals(
                AudioResult.Value(Unit),
                f.bridge.append(
                    AudioStorageUnitIdentity(audio, id(), index, index * 80_000L),
                    AudioFormat.PCM,
                    ByteArray(160_000) { (it % 127).toByte() },
                ),
            )
        }
        return audio
    }

    private fun id() = UUID.randomUUID().toString()
}
