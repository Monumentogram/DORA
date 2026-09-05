package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.AliasObservation
import com.monumentogram.dora.poc.recovery.controller.ConfirmationArtifactSnapshot
import com.monumentogram.dora.poc.recovery.controller.ConfirmationPathObservation
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.crypto.newTestAead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryMicrofileReconciliationControllerTest {
    @Test
    fun `actual Tink confirmation manifest and unit mint exact consumed prefix proof`() {
        val fixture = Fixture()
        val result =
            fixture.controller.reconcile(fixture.run)
                as MicrofileReconciliationResult.AuthenticatedPrefix
        assertArrayEquals(fixture.plaintext, result.prefix.plaintextSnapshot())
        assertEquals(1UL, result.capability.manifestGenerationUsed)
        assertEquals(1, result.capability.authenticatedUnitCount)
        assertEquals(fixture.plaintext.size.toULong(), result.capability.authenticatedEndExclusive)
        assertTrue(result.capability.authorizes(result.prefix))
    }

    @Test
    fun `later malformed row cannot discard earlier authenticated prefix`() {
        val fixture = Fixture(addMalformedLaterRow = true)
        val result =
            fixture.controller.reconcile(fixture.run) as MicrofileReconciliationResult.PartialPrefix
        assertArrayEquals(fixture.plaintext, result.prefix.plaintextSnapshot())
        assertEquals(1UL, result.capability.manifestGenerationUsed)
        assertEquals(ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID, result.diagnostic)
    }

    @Test
    fun `caller cannot forge authenticated prefix capability`() {
        val fixture = Fixture()
        val result =
            fixture.controller.reconcile(fixture.run)
                as MicrofileReconciliationResult.AuthenticatedPrefix
        assertThrows(IllegalStateException::class.java) {
            AuthenticatedMicrofilePrefixCapability(
                fixture.run,
                99UL,
                1,
                4UL,
                result.capability.authenticatedPlaintextSha256,
                result.capability.manifestCiphertextSha256,
                result.capability.orderedAuthenticatedRowsDigest,
                Any(),
            )
        }
    }

    @Test
    fun `KCB05 final orphan requires actual Tink proof before quarantine`() {
        val fixture = Fixture()
        val authenticated =
            fixture.reconcileFinalOrphan(false)
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertTrue(authenticated.quarantine is QuarantineResult.Completed)
        assertTrue("rename" in fixture.quarantineEvents)

        fixture.quarantineEvents.clear()
        val unverifiable =
            fixture.reconcileFinalOrphan(true)
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(null, unverifiable.quarantine)
        assertTrue(fixture.quarantineEvents.isEmpty())
    }

    private class Fixture(addMalformedLaterRow: Boolean = false) {
        val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val plaintext = byteArrayOf(1, 2, 3, 4)
        private val runAead =
            RecoveryRunAeadProvider(
                    object : RecoveryRunAeadBackend {
                        private val aead = newTestAead()

                        override fun generateNew(keyUri: String) = Unit

                        override fun getAead(keyUri: String) = aead
                    }
                )
                .createNew(run)
        private val previous = Sha256Value.ZERO
        private val unitAad =
            MicrofileAad(RecoveryCandidate.MICROFILE, run, 1UL, 0UL, 0UL, 4UL, 5UL, previous)
        private val unitEnvelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                run,
                KeyEnvelopeTargetKind.MICROFILE,
                1UL,
                0UL,
                0UL,
                4UL,
                5UL,
                previous,
            )
        private val unitKeyset = RecoveryTinkRuntime.newAeadKeyset(unitEnvelopeAad)
        private val unitEnvelope = unitKeyset.serializeEncrypted(runAead)
        private val unitCiphertext = unitKeyset.encryptMicrofile(plaintext, unitAad)
        private val unitRow =
            RecoveryMicrofileUnitRow(
                run.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                0UL,
                0UL,
                4UL,
                5UL,
                RecoveryRelativeNames.microfileCiphertext(0UL),
                unitCiphertext.size.toLong(),
                Sha256Value.calculate(unitCiphertext),
                RecoveryRelativeNames.microfileKeyEnvelope(0UL),
                unitEnvelope.size.toLong(),
                Sha256Value.calculate(unitEnvelope),
                1UL,
                Sha256Value.calculate(byteArrayOf(9)),
            )
        private val entry =
            RecoveryManifestEntry(
                0UL,
                0UL,
                4UL,
                5UL,
                unitCiphertext.size.toULong(),
                Sha256Value.calculate(unitCiphertext),
                unitEnvelope.size.toULong(),
                Sha256Value.calculate(unitEnvelope),
                unitRow.ciphertextRelativeName,
                unitRow.keyEnvelopeRelativeName,
            )
        private val manifest =
            RecoveryManifest.create(
                RecoveryCandidate.MICROFILE,
                run,
                1UL,
                previous,
                4UL,
                listOf(entry),
            )
        private val manifestEnvelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                run,
                KeyEnvelopeTargetKind.MANIFEST,
                1UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                4UL,
                0UL,
                previous,
            )
        private val manifestKeyset = RecoveryTinkRuntime.newAeadKeyset(manifestEnvelopeAad)
        private val manifestEnvelope = manifestKeyset.serializeEncrypted(runAead)
        private val publicationAad =
            PublicationAad(
                RecoveryCandidate.MICROFILE,
                run,
                PublicationKind.MANIFEST,
                1UL,
                0UL,
                4UL,
                previous,
            )
        private val manifestCiphertext =
            manifestKeyset.encryptPublication(
                RecoveryManifestCodec.encode(manifest),
                publicationAad,
            )
        private val publication =
            RecoveryManifestPublicationRow(
                run.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                PublicationKind.MANIFEST,
                1UL,
                4UL,
                RecoveryRelativeNames.manifestCiphertext(1UL),
                manifestCiphertext.size.toLong(),
                Sha256Value.calculate(manifestCiphertext),
                RecoveryRelativeNames.manifestKeyEnvelope(1UL),
                manifestEnvelope.size.toLong(),
                Sha256Value.calculate(manifestEnvelope),
                previous,
            )
        private val confirmationValue = KeyConfirmationValue(RecoveryCandidate.MICROFILE, run)
        private val confirmationCiphertext = runAead.encryptKeyConfirmation(confirmationValue)
        private val confirmation =
            KeyConfirmationSnapshot(
                confirmationValue,
                StoredKeyConfirmationIdentity(
                    confirmationValue,
                    "key-confirmation/run.kc",
                    confirmationCiphertext.size.toLong(),
                    Sha256Value.calculate(confirmationCiphertext),
                    confirmationValue.canonicalAliasSha256,
                ),
                ConfirmationArtifactSnapshot(
                    "key-confirmation/run.kc",
                    ConfirmationPathObservation(true, true, true, true),
                    confirmationCiphertext,
                ),
                false,
                AliasObservation.PRESENT,
            )
        private val malformed = unitRow.copy(unitIndex = 2UL, manifestGeneration = 3UL)
        private val candidate =
            RecoveryCandidateSnapshot(
                listOf(
                    CandidateBootstrapRow(
                        run.toCanonicalString(),
                        RecoveryCandidate.MICROFILE.contractId,
                        KeyConfirmationState.VALID,
                    )
                ),
                if (addMalformedLaterRow) listOf(unitRow, malformed) else listOf(unitRow),
                listOf(publication),
            )
        private val artifacts =
            mapOf(
                unitRow.keyEnvelopeRelativeName to
                    RecoveryArtifactBytes(unitRow.keyEnvelopeRelativeName, unitEnvelope),
                unitRow.ciphertextRelativeName to
                    RecoveryArtifactBytes(unitRow.ciphertextRelativeName, unitCiphertext),
                publication.keyEnvelopeRelativeName to
                    RecoveryArtifactBytes(publication.keyEnvelopeRelativeName, manifestEnvelope),
                publication.publicationRelativeName to
                    RecoveryArtifactBytes(publication.publicationRelativeName, manifestCiphertext),
            )
        private val crypto = ActualRecoveryCrypto(runAead)

        private fun source(confirmationValue: KeyConfirmationSnapshot = confirmation) =
            object : RecoveryReconciliationSource {
                override fun loadConfirmation(runId: RunId) = confirmationValue

                override fun loadCandidate(runId: RunId) = candidate

                override fun loadArtifact(runId: RunId, relativeName: String) =
                    artifacts[relativeName]
            }

        val controller =
            RecoveryMicrofileReconciliationController(
                source(),
                crypto,
                com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController {
                    runAead
                },
            )

        val quarantineEvents = mutableListOf<String>()

        fun reconcileFinalOrphan(corrupt: Boolean): MicrofileReconciliationResult {
            val bytes =
                confirmationCiphertext.copyOf().also {
                    if (corrupt) it[0] = (it[0].toInt() xor 1).toByte()
                }
            val orphanConfirmation =
                confirmation.copy(
                    durableRow = null,
                    finalArtifact =
                        ConfirmationArtifactSnapshot(
                            confirmation.finalArtifact!!.relativeName,
                            confirmation.finalArtifact.path,
                            bytes,
                        ),
                )
            val qJournal = MemoryQuarantineJournal(quarantineEvents)
            val qStorage = MemoryQuarantineStorage(quarantineEvents)
            val orphanController =
                RecoveryMicrofileReconciliationController(
                    source(orphanConfirmation),
                    crypto,
                    com.monumentogram.dora.poc.recovery.controller
                        .RecoveryKeyConfirmationController { runAead },
                    RecoveryQuarantineController(qStorage, qJournal) {
                        quarantineEvents += "evidence"
                    },
                )
            return orphanController.reconcile(run)
        }
    }

    private class MemoryQuarantineStorage(private val events: MutableList<String>) :
        RecoveryQuarantineStorage {
        private var observation =
            QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT)

        override fun prepare(runId: RunId) {
            events += "prepare"
        }

        override fun inspect(row: RecoveryQuarantineIntentRow) = observation

        override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
            events += "rename"
            observation =
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT)
        }

        override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) = Unit

        override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) = Unit
    }

    private class MemoryQuarantineJournal(private val events: MutableList<String>) :
        RecoveryQuarantineJournal {
        private var row: RecoveryQuarantineIntentRow? = null

        override fun load(intentId: Sha256Value) = row

        override fun beginNonExclusive(): RecoveryQuarantineTransaction =
            object : RecoveryQuarantineTransaction {
                private var inserted: RecoveryQuarantineIntentRow? = null
                private var completed = false

                override fun insert(row: RecoveryQuarantineIntentRow) {
                    inserted = row
                }

                override fun complete(intentId: Sha256Value) {
                    completed = true
                }

                override fun markSuccessful() = Unit

                override fun end() {
                    inserted?.let { row = it }
                    if (completed) row = row?.copy(state = QuarantineIntentState.COMPLETED)
                }
            }
    }

    private class ActualRecoveryCrypto(private val runAead: RecoveryRunAead) :
        RecoveryReconciliationCrypto {
        override fun authenticateConfirmationOrphan(
            expected: KeyConfirmationValue,
            ciphertext: ByteArray,
        ): KeyConfirmationDecryption = runAead.decryptKeyConfirmation(ciphertext, expected)

        override fun authenticateManifest(
            runId: RunId,
            publication: RecoveryManifestPublicationRow,
            previousDigest: Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): RecoveryManifest {
            val envelopeAad =
                KeyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    runId,
                    KeyEnvelopeTargetKind.MANIFEST,
                    publication.generation,
                    KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                    0UL,
                    publication.committedEndExclusive,
                    0UL,
                    previousDigest,
                )
            val keyset =
                RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, envelopeAad)
            val aad =
                PublicationAad(
                    RecoveryCandidate.MICROFILE,
                    runId,
                    PublicationKind.MANIFEST,
                    publication.generation,
                    publication.generation - 1UL,
                    publication.committedEndExclusive,
                    previousDigest,
                )
            return RecoveryManifestCodec.decode(keyset.decryptPublication(ciphertext, aad))
        }

        override fun authenticateUnit(
            runId: RunId,
            unit: RecoveryMicrofileUnitRow,
            previousDigest: Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray {
            val envelopeAad =
                KeyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    runId,
                    KeyEnvelopeTargetKind.MICROFILE,
                    unit.manifestGeneration,
                    unit.unitIndex,
                    unit.plaintextStartInclusive,
                    unit.plaintextEndExclusive,
                    unit.cadenceSeconds,
                    previousDigest,
                )
            val keyset =
                RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, envelopeAad)
            val aad =
                MicrofileAad(
                    RecoveryCandidate.MICROFILE,
                    runId,
                    unit.manifestGeneration,
                    unit.unitIndex,
                    unit.plaintextStartInclusive,
                    unit.plaintextEndExclusive,
                    unit.cadenceSeconds,
                    previousDigest,
                )
            return keyset.decryptMicrofile(ciphertext, aad)
        }
    }
}
