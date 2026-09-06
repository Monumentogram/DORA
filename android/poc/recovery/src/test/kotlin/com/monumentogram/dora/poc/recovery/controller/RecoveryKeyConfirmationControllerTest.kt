package com.monumentogram.dora.poc.recovery.controller

import com.google.crypto.tink.Aead
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationAadCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationPlaintextCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.RecordingRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryCryptoTestFixtures
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooManyFunctions")
class RecoveryKeyConfirmationControllerTest {
    @Test
    fun `all bootstrap remainder combinations retain evidence without opening an alias`() {
        val combinations =
            listOf(
                BootstrapEvidence(false, false, AliasObservation.ABSENT),
                BootstrapEvidence(false, true, AliasObservation.ABSENT),
                BootstrapEvidence(true, false, AliasObservation.ABSENT),
                BootstrapEvidence(true, true, AliasObservation.ABSENT),
                BootstrapEvidence(false, false, AliasObservation.PRESENT),
                BootstrapEvidence(false, true, AliasObservation.PRESENT),
                BootstrapEvidence(true, false, AliasObservation.PRESENT),
                BootstrapEvidence(true, true, AliasObservation.PRESENT),
            )
        for (expectedEvidence in combinations) {
            val fixture = Fixture()
            val snapshot =
                fixture.snapshot(
                    row = null,
                    finalPresent = expectedEvidence.finalPresent,
                    temporary = expectedEvidence.temporaryPresent,
                    alias = expectedEvidence.alias,
                )
            val result = fixture.controller.evaluate(snapshot)
            if (expectedEvidence == BootstrapEvidence(false, false, AliasObservation.ABSENT)) {
                assertEquals(ConfirmationResult.Absent(expectedEvidence), result)
            } else {
                assertEquals(
                    ConfirmationResult.Rejected(
                        KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP,
                        ConfirmationPhase.BOOTSTRAP,
                        expectedEvidence,
                    ),
                    result,
                )
            }
            assertEquals(result, fixture.controller.evaluate(snapshot))
            assertTrue(fixture.backend.events.isEmpty())
            assertEquals(0, fixture.primitive.decryptCalls)
        }
    }

    @Test
    fun `durable row missing final precedes unavailable alias and never promotes temp`() {
        val fixture = Fixture()
        val result =
            fixture.controller.evaluate(
                fixture.snapshot(
                    finalPresent = false,
                    temporary = true,
                    alias = AliasObservation.ABSENT,
                )
            )
        assertRejected(
            result,
            KeyRecoveryClassification.KEY_CONFIRMATION_MISSING,
            ConfirmationPhase.STORED_IDENTITY,
        )
        assertTrue(fixture.backend.events.isEmpty())
    }

    @Test
    fun `valid ciphertext confirms each candidate repeatedly without creating a key`() {
        RecoveryCandidate.entries.forEach { candidate ->
            val fixture =
                Fixture(KeyConfirmationValue(candidate, RecoveryCryptoTestFixtures.RUN_ID))
            val snapshot = fixture.snapshot()
            repeat(2) {
                assertEquals(
                    ConfirmationResult.Validated(fixture.expected, snapshot.evidence),
                    fixture.controller.evaluate(snapshot),
                )
            }
            assertEquals(
                listOf(
                    "get:${fixture.expected.canonicalAlias}",
                    "get:${fixture.expected.canonicalAlias}",
                ),
                fixture.backend.events,
            )
            assertEquals(2, fixture.primitive.decryptCalls)
        }
    }

    @Test
    fun `every stored identity mismatch rejects before alias open or decrypt`() {
        val fixture = Fixture()
        val identity = fixture.identity
        val badRows =
            listOf(
                identity.copy(
                    value = KeyConfirmationValue(RecoveryCandidate.MICROFILE, identity.value.runId)
                ),
                identity.copy(
                    value =
                        KeyConfirmationValue(
                            identity.value.candidate,
                            RecoveryCryptoTestFixtures.OTHER_RUN_ID,
                        )
                ),
                identity.copy(relativeName = "key-confirmation/../run.kc"),
                identity.copy(ciphertextBytes = identity.ciphertextBytes + 1),
                identity.copy(ciphertextSha256 = Sha256Value.ZERO),
                identity.copy(canonicalAliasSha256 = Sha256Value.ZERO),
            )
        badRows.forEach { row ->
            assertRejected(
                fixture.controller.evaluate(
                    fixture.snapshot(row = row, alias = AliasObservation.ABSENT)
                ),
                KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                ConfirmationPhase.STORED_IDENTITY,
            )
        }
        val safe = SAFE_PATH
        listOf(
                safe.copy(containedBeneathRunRoot = false),
                safe.copy(everyExistingComponentLstatObserved = false),
                safe.copy(leafIsRegularFile = false),
                safe.copy(noComponentOrLeafSymlink = false),
            )
            .forEach { path ->
                assertRejected(
                    fixture.controller.evaluate(fixture.snapshot(path = path)),
                    KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                    ConfirmationPhase.STORED_IDENTITY,
                )
            }
        assertRejected(
            fixture.controller.evaluate(fixture.snapshot(finalName = "other/run.kc")),
            KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
            ConfirmationPhase.STORED_IDENTITY,
        )
        assertTrue(fixture.backend.events.isEmpty())
        assertEquals(0, fixture.primitive.decryptCalls)
    }

    @Test
    fun `missing invalidated and unusable alias observations fail closed after identity passes`() {
        listOf(AliasObservation.ABSENT, AliasObservation.INVALIDATED, AliasObservation.UNUSABLE)
            .forEach { alias ->
                val fixture = Fixture()
                assertRejected(
                    fixture.controller.evaluate(fixture.snapshot(alias = alias)),
                    KeyRecoveryClassification.KEY_UNAVAILABLE,
                    ConfirmationPhase.ALIAS_ACCESS,
                )
                assertTrue(fixture.backend.events.isEmpty())
            }
    }

    @Test
    fun `open failure cannot generate a replacement and provider errors remain diagnostic`() {
        val fixture = Fixture()
        fixture.backend.getFailure = GeneralSecurityException("synthetic unavailable alias")
        assertRejected(
            fixture.controller.evaluate(fixture.snapshot()),
            KeyRecoveryClassification.KEY_UNAVAILABLE,
            ConfirmationPhase.ALIAS_ACCESS,
        )
        fixture.backend.getFailure = ProviderException("synthetic provider failure")
        assertDiagnostic(
            fixture.controller.evaluate(fixture.snapshot()),
            ConfirmationDiagnostic.OPEN_OPERATIONAL_FAILURE,
        )
        fixture.backend.getFailure = IllegalStateException("synthetic unexpected failure")
        assertDiagnostic(
            fixture.controller.evaluate(fixture.snapshot()),
            ConfirmationDiagnostic.OPEN_UNEXPECTED_FAILURE,
        )
        assertEquals(List(3) { "get:${fixture.expected.canonicalAlias}" }, fixture.backend.events)
        assertEquals(0, fixture.primitive.decryptCalls)
    }

    @Test
    fun `wrong run returned by opener remains diagnostic without decrypt`() {
        val fixture = Fixture()
        val controller = RecoveryKeyConfirmationController {
            RecoveryRunAeadProvider(fixture.backend)
                .openExisting(RecoveryCryptoTestFixtures.OTHER_RUN_ID)
        }
        assertDiagnostic(
            controller.evaluate(fixture.snapshot()),
            ConfirmationDiagnostic.OPEN_IDENTITY_MISMATCH,
        )
        assertEquals(0, fixture.primitive.decryptCalls)
    }

    @Test
    fun `authenticated malformed and wrong identity plaintext is corrupt rather than KEY04`() {
        val fixture = Fixture()
        val valid = KeyConfirmationPlaintextCodec.encode(fixture.expected)
        val malformed =
            listOf(byteArrayOf(1, 2, 3), valid + byteArrayOf(0)) +
                listOf(0, 9, 12, 47, 62, 78).map { offset ->
                    valid.copyOf().apply { this[offset] = (this[offset].toInt() xor 1).toByte() }
                } +
                listOf(
                    KeyConfirmationPlaintextCodec.encode(
                        KeyConfirmationValue(RecoveryCandidate.MICROFILE, fixture.expected.runId)
                    ),
                    KeyConfirmationPlaintextCodec.encode(
                        KeyConfirmationValue(
                            RecoveryCandidate.STREAM,
                            RecoveryCryptoTestFixtures.OTHER_RUN_ID,
                        )
                    ),
                )
        malformed.forEach { plaintext ->
            val ciphertext =
                fixture.primitive.encrypt(
                    plaintext,
                    KeyConfirmationAadCodec.encode(fixture.expected),
                )
            val row = fixture.identityFor(ciphertext)
            val snapshot =
                fixture.snapshot(row = row, ciphertext = ciphertext, replacement = replacement(row))
            assertRejected(
                fixture.controller.evaluate(snapshot),
                KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                ConfirmationPhase.PLAINTEXT_CONTRACT,
            )
        }
        assertEquals(malformed.size, fixture.primitive.decryptCalls)
    }

    @Test
    fun `controlled replacement plus actual authentication failure yields exact KEY04`() {
        val fixture = Fixture()
        fixture.primitive.replaceSyntheticKey()
        assertRejected(
            fixture.controller.evaluate(
                fixture.snapshot(replacement = replacement(fixture.identity))
            ),
            KeyRecoveryClassification.KEY_UNAVAILABLE_KEY_MISMATCH,
            ConfirmationPhase.KEY04,
        )
        assertEquals(1, fixture.primitive.decryptCalls)
        assertEquals(listOf("get:${fixture.expected.canonicalAlias}"), fixture.backend.events)
    }

    @Test
    fun `authentication failure without exact replacement provenance is unclassified`() {
        val fixture = Fixture()
        fixture.primitive.replaceSyntheticKey()
        val receipt = replacement(fixture.identity)
        val wrongIdentities =
            listOf(
                fixture.identity.copy(
                    value =
                        KeyConfirmationValue(RecoveryCandidate.MICROFILE, fixture.expected.runId)
                ),
                fixture.identity.copy(
                    value =
                        KeyConfirmationValue(
                            RecoveryCandidate.STREAM,
                            RecoveryCryptoTestFixtures.OTHER_RUN_ID,
                        )
                ),
                fixture.identity.copy(relativeName = "wrong/run.kc"),
                fixture.identity.copy(ciphertextBytes = fixture.identity.ciphertextBytes + 1),
                fixture.identity.copy(ciphertextSha256 = Sha256Value.ZERO),
                fixture.identity.copy(canonicalAliasSha256 = Sha256Value.ZERO),
            )
        val receipts =
            listOf(null, receipt.copy(eventId = ""), receipt.copy(canonicalAlias = "wrong")) +
                wrongIdentities.map { receipt.copy(preservedIdentity = it) }
        receipts.forEach { provenance ->
            assertDiagnostic(
                fixture.controller.evaluate(fixture.snapshot(replacement = provenance)),
                ConfirmationDiagnostic.AUTHENTICATION_WITHOUT_KEY04_PROVENANCE,
            )
        }
        assertEquals(receipts.size, fixture.primitive.decryptCalls)
    }

    @Test
    fun `successful decrypt cannot be fabricated into KEY04 by a replacement receipt`() {
        val fixture = Fixture()
        val snapshot = fixture.snapshot(replacement = replacement(fixture.identity))
        assertEquals(
            ConfirmationResult.Validated(fixture.expected, snapshot.evidence),
            fixture.controller.evaluate(snapshot),
        )
        assertEquals(1, fixture.primitive.decryptCalls)
    }

    @Test
    fun `copied cross candidate and cross run ciphertext is unclassified without KEY04 provenance`() {
        val fixture = Fixture()
        listOf(
                KeyConfirmationValue(RecoveryCandidate.MICROFILE, fixture.expected.runId),
                KeyConfirmationValue(
                    RecoveryCandidate.STREAM,
                    RecoveryCryptoTestFixtures.OTHER_RUN_ID,
                ),
            )
            .forEach { wrongValue ->
                val ciphertext =
                    fixture.primitive.encrypt(
                        KeyConfirmationPlaintextCodec.encode(wrongValue),
                        KeyConfirmationAadCodec.encode(wrongValue),
                    )
                assertDiagnostic(
                    fixture.controller.evaluate(
                        fixture.snapshot(
                            row = fixture.identityFor(ciphertext),
                            ciphertext = ciphertext,
                        )
                    ),
                    ConfirmationDiagnostic.AUTHENTICATION_WITHOUT_KEY04_PROVENANCE,
                )
            }
    }

    @Test
    fun `unknown operational and unexpected decrypt exceptions never become a KEY verdict`() {
        val fixture = Fixture()
        listOf(
                GeneralSecurityException("synthetic unknown") to
                    ConfirmationDiagnostic.DECRYPT_UNKNOWN_FAILURE,
                ProviderException("synthetic provider") to
                    ConfirmationDiagnostic.DECRYPT_OPERATIONAL_FAILURE,
                IllegalStateException("synthetic unexpected") to
                    ConfirmationDiagnostic.DECRYPT_UNEXPECTED_FAILURE,
            )
            .forEach { (error, expected) ->
                fixture.primitive.decryptFailure = error
                assertDiagnostic(
                    fixture.controller.evaluate(
                        fixture.snapshot(replacement = replacement(fixture.identity))
                    ),
                    expected,
                )
            }
    }

    @Test
    fun `snapshot copies prevent caller mutation and mutation during alias open from changing checked ciphertext`() {
        val fixture = Fixture()
        val supplied = fixture.ciphertext.copyOf()
        val snapshot = fixture.snapshot(ciphertext = supplied)
        supplied.fill(0)
        snapshot.finalArtifact!!.ciphertextSnapshot().fill(0)
        val controller = RecoveryKeyConfirmationController {
            supplied.fill(1)
            snapshot.finalArtifact.ciphertextSnapshot().fill(2)
            RecoveryRunAeadProvider(fixture.backend).openExisting(it)
        }
        repeat(2) {
            assertEquals(
                ConfirmationResult.Validated(fixture.expected, snapshot.evidence),
                controller.evaluate(snapshot),
            )
        }
        assertEquals(2, fixture.primitive.decryptCalls)
    }

    private fun assertRejected(
        result: ConfirmationResult,
        expected: KeyRecoveryClassification,
        phase: ConfirmationPhase,
    ) {
        assertTrue(result is ConfirmationResult.Rejected)
        result as ConfirmationResult.Rejected
        assertEquals(expected, result.classification)
        assertEquals(phase, result.phase)
    }

    private fun assertDiagnostic(result: ConfirmationResult, expected: ConfirmationDiagnostic) {
        assertTrue(result is ConfirmationResult.Unclassified)
        assertEquals(expected, (result as ConfirmationResult.Unclassified).diagnostic)
    }

    private fun replacement(identity: StoredKeyConfirmationIdentity): ControlledKey04Replacement =
        ControlledKey04Replacement("synthetic-key04-event", identity.value.canonicalAlias, identity)

    private class Fixture(
        val expected: KeyConfirmationValue =
            KeyConfirmationValue(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.RUN_ID)
    ) {
        val primitive = FixedSyntheticAead()
        val backend = RecordingRunAeadBackend(primitive)
        val controller =
            RecoveryKeyConfirmationController(RecoveryRunAeadProvider(backend)::openExisting)
        val ciphertext =
            primitive.encrypt(
                KeyConfirmationPlaintextCodec.encode(expected),
                KeyConfirmationAadCodec.encode(expected),
            )
        val identity = identityFor(ciphertext)

        fun identityFor(bytes: ByteArray): StoredKeyConfirmationIdentity =
            StoredKeyConfirmationIdentity(
                expected,
                "key-confirmation/run.kc",
                bytes.size.toLong(),
                Sha256Value.calculate(bytes),
                expected.canonicalAliasSha256,
            )

        @Suppress("LongParameterList")
        fun snapshot(
            row: StoredKeyConfirmationIdentity? = identity,
            finalPresent: Boolean = true,
            temporary: Boolean = false,
            alias: AliasObservation = AliasObservation.PRESENT,
            path: ConfirmationPathObservation = SAFE_PATH,
            finalName: String = "key-confirmation/run.kc",
            ciphertext: ByteArray = this.ciphertext,
            replacement: ControlledKey04Replacement? = null,
        ): KeyConfirmationSnapshot =
            KeyConfirmationSnapshot(
                expected,
                row,
                if (finalPresent) ConfirmationArtifactSnapshot(finalName, path, ciphertext)
                else null,
                temporary,
                alias,
                replacement,
            )
    }

    /** Fixed synthetic key/nonce is test-only; no production encryption uses this backend. */
    private class FixedSyntheticAead : Aead {
        private var key = ByteArray(32) { it.toByte() }
        var decryptCalls = 0
        var decryptFailure: Exception? = null

        fun replaceSyntheticKey() {
            key = ByteArray(32) { (it + 1).toByte() }
        }

        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
            crypt(Cipher.ENCRYPT_MODE, plaintext, associatedData)

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            decryptCalls += 1
            decryptFailure?.let { throw it }
            return crypt(Cipher.DECRYPT_MODE, ciphertext, associatedData)
        }

        private fun crypt(mode: Int, bytes: ByteArray, aad: ByteArray): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(
                    mode,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, ByteArray(12) { (it + 32).toByte() }),
                )
                updateAAD(aad)
                doFinal(bytes)
            }
    }

    companion object {
        private val SAFE_PATH = ConfirmationPathObservation(true, true, true, true)
    }
}
