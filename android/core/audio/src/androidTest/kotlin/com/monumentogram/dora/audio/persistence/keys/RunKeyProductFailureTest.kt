@file:Suppress("WildcardImport", "NestedBlockDepth") // Explicit stage/category fault matrix.

package com.monumentogram.dora.audio.persistence.keys

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.monumentogram.dora.audio.*
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.security.ProviderException
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class RunKeyProductFailureTest {
    private fun id() = UUID.randomUUID().toString()

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    private class Faults : VaultKeystoreIo by AndroidVaultKeystoreIo {
        val aliases = mutableListOf<String>()
        var stage = ""
        var failure: Exception = ProviderException("provider-path-canary")
        var target: String? = null
        var current = ""
        var opens = 0
        var missingOpen = false
        var failOnOpen: Int? = null
        var ciphers = 0
        var failOnCipher: Int? = null

        private fun hit(at: String, alias: String) {
            val selectedAlias = target == null || target == alias
            val selectedCall = stage == at || (at == "open" && opens == failOnOpen)
            if (selectedAlias && selectedCall) throw failure
        }

        override fun generate(alias: String, strongBox: Boolean) {
            AndroidVaultKeystoreIo.generate(alias, strongBox)
            aliases += alias
        }

        override fun exists(alias: String): Boolean {
            hit("exists", alias)
            return AndroidVaultKeystoreIo.exists(alias)
        }

        override fun open(alias: String): SecretKey? {
            current = alias
            opens++
            hit("open", alias)
            return if (missingOpen) null else AndroidVaultKeystoreIo.open(alias)
        }

        override fun cipher(): Cipher {
            ciphers++
            if (ciphers == failOnCipher) throw failure
            hit("cipher", current)
            return AndroidVaultKeystoreIo.cipher()
        }
    }

    @Test
    fun originalPlatformFailuresMapThroughAcceptedControllersAndSuccessfulRetryIsClean() {
        val faults = Faults()
        RunKeyProductFixture(faults).use { f ->
            val audio = identity()
            assertEquals(AudioResult.Value(Unit), f.bridge.create(audio))
            assertEquals(
                AudioResult.Value(Unit),
                f.bridge.append(
                    AudioStorageUnitIdentity(audio, id(), 0, 0),
                    AudioFormat.PCM,
                    byteArrayOf(1, 2),
                ),
            )
            for (stage in listOf("exists", "open", "cipher", "decrypt")) {
                for ((failure, expected) in
                    listOf(
                        ProviderException("provider-path-canary") to AudioFailure.KEY_UNAVAILABLE,
                        ProviderException(
                            "provider-path-canary",
                            KeyPermanentlyInvalidatedException(),
                        ) to AudioFailure.KEY_INVALIDATED,
                        ProviderException(
                            "provider-path-canary",
                            AEADBadTagException("cipher-canary"),
                        ) to AudioFailure.AUTHENTICATION_FAILED,
                        KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT) to AudioFailure.CORRUPT,
                    )) {
                    faults.stage = stage
                    faults.ciphers = 0
                    faults.failOnCipher = if (stage == "decrypt") 3 else null
                    faults.failure = failure
                    val result =
                        f.bridge.extract(audio) { _, _ -> error("Rejected key delivered bytes") }
                    assertEquals("$stage/$expected", AudioResult.Failed(expected), result)
                    assertFalse(result.toString().contains("canary"))
                    faults.stage = ""
                    faults.failOnCipher = null
                    var actual = byteArrayOf()
                    assertTrue(
                        f.bridge.extract(audio) { _, bytes -> actual = bytes.copyOf() }
                            is AudioResult.Value
                    )
                    assertArrayEquals(byteArrayOf(1, 2), actual)
                }
            }
            faults.missingOpen = true
            assertEquals(
                AudioResult.Failed(AudioFailure.KEY_INVALIDATED),
                f.bridge.extract(audio) { _, _ -> error("Missing key delivered bytes") },
            )
        }
    }

    @Test
    fun laterTemporaryAndPermanentFailureKeepExactPriorPrefixAndPreventFinalization() {
        val faults = Faults()
        RunKeyProductFixture(faults).use { f ->
            val audio = identity()
            f.bridge.create(audio)
            assertEquals(
                AudioResult.Value(Unit),
                f.bridge.append(
                    AudioStorageUnitIdentity(audio, id(), 0, 0),
                    AudioFormat.PCM,
                    byteArrayOf(1, 2, 3, 4),
                ),
            )
            val second = RunId.fromCanonicalString(id())
            assertEquals(
                AudioResult.Value(Unit),
                f.bridge.append(
                    AudioStorageUnitIdentity(audio, second.toCanonicalString(), 1, 2),
                    AudioFormat.PCM,
                    byteArrayOf(5, 6),
                ),
            )
            faults.target = faults.aliases.last()
            for ((failure, expected) in
                listOf(
                    ProviderException("provider-path-canary") to AudioFailure.KEY_UNAVAILABLE,
                    ProviderException(
                        "provider-path-canary",
                        KeyPermanentlyInvalidatedException(),
                    ) to AudioFailure.KEY_INVALIDATED,
                )) {
                faults.stage = "open"
                faults.failure = failure
                assertPrefix(f, audio, expected)
            }
            faults.failure =
                ProviderException("provider-canary", AEADBadTagException("cipher-canary"))
            assertEquals(
                AudioResult.Failed(AudioFailure.AUTHENTICATION_FAILED),
                f.bridge.extract(audio) { _, _ -> },
            )
            faults.stage = ""
            f.backend.removeAlias(second)
            assertPrefix(f, audio, AudioFailure.KEY_INVALIDATED)
            assertEquals(AudioResult.Failed(AudioFailure.KEY_INVALIDATED), f.bridge.finalize(audio))
            requireNotNull(f.journal.catalog.tryAcquire(audio)).use {
                assertTrue(f.journal.catalog.load(audio)?.pending is AudioIntent.Finalize)
            }
        }
    }

    @Test
    fun failedBootstrapAndPublicationKeepDurableIntentAndNeverRetryGeneration() {
        for (open in listOf(1, 2)) {
            val faults = Faults().apply { failOnOpen = open }
            RunKeyProductFixture(faults).use { f ->
                val audio = identity()
                val unit = AudioStorageUnitIdentity(audio, id(), 0, 0)
                f.bridge.create(audio)
                assertEquals(
                    "open=$open",
                    AudioResult.Failed(AudioFailure.KEY_UNAVAILABLE),
                    f.bridge.append(unit, AudioFormat.PCM, byteArrayOf(1, 2)),
                )
                assertEquals(1, faults.aliases.size)
                faults.failOnOpen = null
                assertEquals(
                    AudioResult.Failed(AudioFailure.COLLISION),
                    f.bridge.append(unit, AudioFormat.PCM, byteArrayOf(1, 2)),
                )
                requireNotNull(f.journal.catalog.tryAcquire(audio)).use {
                    assertTrue(f.journal.catalog.load(audio)?.pending is AudioIntent.Append)
                    val committed =
                        f.journal.loadBootstrapIdentity(RunId.fromCanonicalString(unit.unitId))
                    assertEquals(
                        "Second open is publication after committed bootstrap",
                        open == 2,
                        committed != null,
                    )
                }
                assertEquals(1, faults.aliases.size)
            }
        }
    }

    @Test
    fun corruptConfirmationWithMissingAliasNeverBecomesSuccessfulPartialRecovery() {
        combinedConfirmationAndMissingAlias(false, AudioFailure.CORRUPT)
    }

    @Test
    fun missingConfirmationWithMissingAliasPreservesAcceptedMissingArtifactReason() {
        combinedConfirmationAndMissingAlias(true, AudioFailure.INCOMPLETE)
    }

    private fun combinedConfirmationAndMissingAlias(missing: Boolean, expected: AudioFailure) {
        RunKeyProductFixture(Faults()).use { f ->
            val audio = identity()
            f.bridge.create(audio)
            val first = AudioStorageUnitIdentity(audio, id(), 0, 0)
            val second = AudioStorageUnitIdentity(audio, id(), 1, 1)
            for (unit in listOf(first, second)) {
                assertEquals(
                    AudioResult.Value(Unit),
                    f.bridge.append(unit, AudioFormat.PCM, byteArrayOf(1, 2)),
                )
            }
            val confirmation =
                java.io.File(
                    f.context.noBackupFilesDir,
                    "dora-vault-v1/poc-recovery/v1/runs/${second.unitId}/key-confirmation/run.kc",
                )
            if (missing) assertTrue(confirmation.delete())
            else {
                val bytes = confirmation.readBytes()
                bytes[1] = (bytes[1].toInt() xor 1).toByte()
                confirmation.writeBytes(bytes)
            }
            f.backend.removeAlias(RunId.fromCanonicalString(second.unitId))
            val result =
                f.bridge.extract(audio) { frame, bytes ->
                    assertEquals(0L, frame)
                    assertArrayEquals(byteArrayOf(1, 2), bytes)
                }
            if (missing) {
                assertTrue(result is AudioResult.Value)
                assertEquals(expected, (result as AudioResult.Value).value.tailFailure)
            } else assertEquals(AudioResult.Failed(expected), result)
        }
    }

    private fun assertPrefix(
        f: RunKeyProductFixture,
        audio: AudioIdentity,
        expected: AudioFailure,
    ) {
        var actual = byteArrayOf()
        val result =
            f.bridge.extract(audio) { frame, bytes ->
                assertEquals(0L, frame)
                actual += bytes
            } as AudioResult.Value
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), actual)
        assertEquals(2L, result.value.frames)
        assertEquals(125L, result.value.durationUs)
        assertEquals(AudioCompletion.PARTIAL_RECOVERED, result.value.completion)
        assertEquals(expected, result.value.tailFailure)
    }
}
