package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
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
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
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
    @Suppress("LongMethod")
    fun `caller cannot forge authenticated prefix capability`() {
        val fixture = Fixture()
        val result =
            fixture.controller.reconcile(fixture.run)
                as MicrofileReconciliationResult.AuthenticatedPrefix
        assertThrows(IllegalStateException::class.java) {
            AuthenticatedMicrofilePrefixCapability(
                RecoveryCandidate.MICROFILE,
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
        val changedRow =
            result.prefix.units
                .single()
                .copy(processingIntentId = Sha256Value.calculate(byteArrayOf(99)))
        val changedRows =
            AuthenticatedMicrofilePrefix(
                RecoveryCandidate.MICROFILE,
                fixture.run,
                result.prefix.manifestGenerationUsed,
                result.prefix.authenticatedEndExclusive,
                result.prefix.plaintextSnapshot(),
                listOf(changedRow),
                result.prefix.manifestCiphertextSha256,
            )
        assertTrue(!result.capability.authorizes(changedRows))
        val changedCandidate =
            AuthenticatedMicrofilePrefix(
                RecoveryCandidate.STREAM,
                fixture.run,
                result.prefix.manifestGenerationUsed,
                result.prefix.authenticatedEndExclusive,
                result.prefix.plaintextSnapshot(),
                result.prefix.units,
                result.prefix.manifestCiphertextSha256,
            )
        assertTrue(!result.capability.authorizes(changedCandidate))

        val row = result.prefix.units.single()
        val rowVariants =
            listOf(
                row.copy(runId = "10112233-4455-6677-8899-aabbccddeeff"),
                row.copy(candidateId = RecoveryCandidate.STREAM.contractId),
                row.copy(unitIndex = 1UL),
                row.copy(plaintextStartInclusive = 1UL),
                row.copy(plaintextEndExclusive = 3UL),
                row.copy(cadenceSeconds = 15UL),
                row.copy(ciphertextRelativeName = "units/u-99.bin"),
                row.copy(ciphertextBytes = row.ciphertextBytes + 1),
                row.copy(ciphertextSha256 = Sha256Value.calculate(byteArrayOf(31))),
                row.copy(keyEnvelopeRelativeName = "key-envelopes/unit-99.bin"),
                row.copy(keyEnvelopeBytes = row.keyEnvelopeBytes + 1),
                row.copy(keyEnvelopeSha256 = Sha256Value.calculate(byteArrayOf(32))),
                row.copy(manifestGeneration = 2UL),
                row.copy(processingIntentId = Sha256Value.calculate(byteArrayOf(33))),
                row.copy(state = "INVALID"),
            )
        rowVariants.forEach { changed ->
            val changedPrefix =
                AuthenticatedMicrofilePrefix(
                    result.prefix.candidate,
                    result.prefix.runId,
                    result.prefix.manifestGenerationUsed,
                    result.prefix.authenticatedEndExclusive,
                    result.prefix.plaintextSnapshot(),
                    listOf(changed),
                    result.prefix.manifestCiphertextSha256,
                )
            assertTrue(!result.capability.authorizes(changedPrefix))
        }
        val malformed = row.copy(ciphertextRelativeName = "x".repeat(1_025))
        val malformedPrefix =
            AuthenticatedMicrofilePrefix(
                result.prefix.candidate,
                result.prefix.runId,
                result.prefix.manifestGenerationUsed,
                result.prefix.authenticatedEndExclusive,
                result.prefix.plaintextSnapshot(),
                listOf(malformed),
                result.prefix.manifestCiphertextSha256,
            )
        assertTrue(!result.capability.authorizes(malformedPrefix))
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

    @Test
    fun `KCB05 rejects unsafe oversize and operational orphan paths before quarantine`() {
        val fixture = Fixture()
        listOf(
                fixture.reconcileFinalOrphan(false, unsafe = true),
                fixture.reconcileFinalOrphan(false, oversize = true),
                fixture.reconcileFinalOrphan(false, cryptoThrows = true),
            )
            .forEach { result ->
                result as MicrofileReconciliationResult.NoAuthenticatedPrefix
                assertEquals(null, result.quarantine)
            }
        assertEquals(
            ReconciliationDiagnostic.UNSAFE_PATH,
            (fixture.reconcileFinalOrphan(false, unsafe = true)
                    as MicrofileReconciliationResult.NoAuthenticatedPrefix)
                .diagnostic,
        )
        assertEquals(
            RecoveryFailureStage.OPERATIONAL,
            (fixture.reconcileFinalOrphan(false, cryptoThrows = true)
                    as MicrofileReconciliationResult.NoAuthenticatedPrefix)
                .failure
                ?.stage,
        )
        assertTrue(fixture.quarantineEvents.isEmpty())
    }

    @Test
    fun `pending moved intent and unreferenced inventory retain authenticated prefix`() {
        val fixture = Fixture()
        val result =
            fixture.reconcilePendingAndInventory()
                as MicrofileReconciliationResult.AuthenticatedPrefix
        assertArrayEquals(fixture.plaintext, result.prefix.plaintextSnapshot())
        assertEquals(2, result.quarantineOutcomes.size)
        assertTrue(result.quarantineOutcomes.all { it is QuarantineResult.Completed })
    }

    @Test
    fun `report only quarantine remnants never create recursive intents`() {
        val fixture = Fixture()
        val result =
            fixture.reconcileReportOnlyQuarantine()
                as MicrofileReconciliationResult.AuthenticatedPrefix
        assertEquals(2, result.inventoryReports.size)
        assertTrue(result.quarantineOutcomes.isEmpty())
        assertTrue(fixture.quarantineEvents.isEmpty())
    }

    @Test
    fun `actual Tink latest manifest authentication failure preserves prior generation`() {
        val fixture = Fixture()
        val result =
            fixture.reconcileCorruptLatestManifest() as MicrofileReconciliationResult.PartialPrefix
        assertArrayEquals(fixture.plaintext, result.prefix.plaintextSnapshot())
        assertEquals(1UL, result.capability.manifestGenerationUsed)
        assertEquals(1, result.capability.authenticatedUnitCount)
        assertEquals(ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID, result.diagnostic)
    }

    @Test
    fun `actual Tink valid latest manifest with wrong publication chain falls back`() {
        val result =
            Fixture().reconcileWrongChainLatestManifest()
                as MicrofileReconciliationResult.PartialPrefix
        assertEquals(1UL, result.capability.manifestGenerationUsed)
        assertEquals(RecoveryFailureStage.MANIFEST_SEMANTICS, result.failure?.stage)
        assertTrue(result.capability.authorizes(result.prefix))
    }

    @Test
    fun `actual Tink unit failures preserve exact zero middle and last prefixes`() {
        listOf(0, 1, 2).forEach { failureIndex ->
            val result =
                Fixture().reconcileThreeUnitsWithAuthFailure(failureIndex)
                    as MicrofileReconciliationResult.PartialPrefix
            assertEquals(failureIndex, result.capability.authenticatedUnitCount)
            assertEquals((failureIndex * 4).toULong(), result.capability.authenticatedEndExclusive)
            assertEquals(null, result.classification)
            assertEquals(ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID, result.diagnostic)
            assertEquals(RecoveryFailureStage.UNIT_PAYLOAD_DECRYPT, result.failure?.stage)
            assertTrue(result.capability.authorizes(result.prefix))
        }
    }

    @Test
    fun `actual Android crypto adapter preserves alias envelope payload and manifest stages`() {
        val diagnostics = Fixture().actualAdapterFailures()
        assertEquals(
            listOf(
                RecoveryFailureStage.ALIAS_OPEN,
                RecoveryFailureStage.ENVELOPE_PARSE,
                RecoveryFailureStage.ENVELOPE_PARSE,
                RecoveryFailureStage.ENVELOPE_PARSE,
                RecoveryFailureStage.UNIT_PAYLOAD_DECRYPT,
                RecoveryFailureStage.MANIFEST_PLAINTEXT,
                RecoveryFailureStage.ALIAS_OPEN,
            ),
            diagnostics.map { it.stage },
        )
        assertEquals(RecoveryFailureCategory.MISSING_ARTIFACT, diagnostics[0].category)
        assertEquals(RecoveryFailureCategory.STRUCTURAL, diagnostics[1].category)
        assertEquals(RecoveryFailureCategory.AUTHENTICATION_REJECTED, diagnostics[2].category)
        assertEquals(RecoveryFailureCategory.UNKNOWN, diagnostics[3].category)
        assertEquals(RecoveryFailureCategory.UNKNOWN, diagnostics[4].category)
        assertEquals(RecoveryFailureCategory.STRUCTURAL, diagnostics[5].category)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, diagnostics[6].category)
    }

    @Test
    fun `controller maps only typed envelope authentication to envelope key failure`() {
        val fixture = Fixture()
        fun result(category: RecoveryFailureCategory, stage: RecoveryFailureStage) =
            fixture.reconcileUnitFailure(
                RecoveryFailureDiagnostic(category, "type", "message", stage)
            )

        assertEquals(
            KeyRecoveryClassification.KEY_ENVELOPE_AUTH_FAILURE,
            result(
                    RecoveryFailureCategory.AUTHENTICATION_REJECTED,
                    RecoveryFailureStage.ENVELOPE_PARSE,
                )
                .classification,
        )
        assertEquals(
            KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE,
            result(RecoveryFailureCategory.STRUCTURAL, RecoveryFailureStage.ENVELOPE_PARSE)
                .classification,
        )
        assertEquals(
            null,
            result(RecoveryFailureCategory.UNKNOWN, RecoveryFailureStage.ENVELOPE_PARSE)
                .classification,
        )
        assertEquals(
            null,
            result(
                    RecoveryFailureCategory.AUTHENTICATION_REJECTED,
                    RecoveryFailureStage.UNIT_PAYLOAD_DECRYPT,
                )
                .classification,
        )
    }

    @Test
    fun `reconciliation accepts 721 rows and retains only that prefix when row 722 exists`() {
        val fixture = Fixture()
        val maximum =
            fixture.reconcileUnitCount(721) as MicrofileReconciliationResult.AuthenticatedPrefix
        assertEquals(721, maximum.capability.authenticatedUnitCount)
        assertEquals(721UL, maximum.capability.authenticatedEndExclusive)

        val over = fixture.reconcileUnitCount(722) as MicrofileReconciliationResult.PartialPrefix
        assertEquals(721, over.capability.authenticatedUnitCount)
        assertEquals(ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID, over.diagnostic)
    }

    @Suppress("LargeClass")
    private class Fixture(addMalformedLaterRow: Boolean = false) {
        val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val plaintext = byteArrayOf(1, 2, 3, 4)
        private val backendAead = newTestAead()
        private val runAead =
            RecoveryRunAeadProvider(
                    object : RecoveryRunAeadBackend {
                        override fun generateNew(keyUri: String) = Unit

                        override fun getAead(keyUri: String) = backendAead
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

        fun actualAdapterFailures(): List<RecoveryFailureDiagnostic> {
            fun adapter(
                aliasFailure: Throwable? = null,
                aead: com.google.crypto.tink.Aead = backendAead,
            ) =
                AndroidRecoveryMicrofileCrypto(
                    RecoveryRunAeadProvider(
                        object : RecoveryRunAeadBackend {
                            override fun generateNew(keyUri: String) = Unit

                            override fun getAead(keyUri: String) =
                                if (aliasFailure != null) throw aliasFailure else aead
                        }
                    )
                )
            val alias =
                adapter(GeneralSecurityException("alias unavailable"))
                    .authenticateUnit(run, unitRow, previous, unitEnvelope, unitCiphertext)
            val malformed =
                adapter().authenticateUnit(run, unitRow, previous, byteArrayOf(1), unitCiphertext)
            val authRejectingAead =
                object : com.google.crypto.tink.Aead {
                    override fun encrypt(
                        plaintext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray = error("unused")

                    override fun decrypt(
                        ciphertext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray =
                        throw GeneralSecurityException("auth", AEADBadTagException("tag"))
                }
            val envelopeAuth =
                adapter(aead = authRejectingAead)
                    .authenticateUnit(run, unitRow, previous, unitEnvelope, unitCiphertext)
            val unknownAead =
                object : com.google.crypto.tink.Aead {
                    override fun encrypt(
                        plaintext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray = error("unused")

                    override fun decrypt(
                        ciphertext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray = throw GeneralSecurityException("undifferentiated")
                }
            val envelopeUnknown =
                adapter(aead = unknownAead)
                    .authenticateUnit(run, unitRow, previous, unitEnvelope, unitCiphertext)
            val corruptPayload =
                unitCiphertext.copyOf().also {
                    it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
                }
            val payload =
                adapter().authenticateUnit(run, unitRow, previous, unitEnvelope, corruptPayload)

            val invalidManifestKeyset = RecoveryTinkRuntime.newAeadKeyset(manifestEnvelopeAad)
            val invalidManifestEnvelope = invalidManifestKeyset.serializeEncrypted(runAead)
            val invalidManifestCiphertext =
                invalidManifestKeyset.encryptPublication(byteArrayOf(1), publicationAad)
            val invalidPublication =
                publication.copy(
                    publicationBytes = invalidManifestCiphertext.size.toLong(),
                    publicationSha256 = Sha256Value.calculate(invalidManifestCiphertext),
                    keyEnvelopeBytes = invalidManifestEnvelope.size.toLong(),
                    keyEnvelopeSha256 = Sha256Value.calculate(invalidManifestEnvelope),
                )
            val manifest =
                adapter()
                    .authenticateManifest(
                        run,
                        invalidPublication,
                        previous,
                        invalidManifestEnvelope,
                        invalidManifestCiphertext,
                    )
            val provider =
                adapter(ProviderException("provider"))
                    .authenticateUnit(run, unitRow, previous, unitEnvelope, unitCiphertext)
            return listOf(alias, malformed, envelopeAuth, envelopeUnknown, payload).map {
                (it as UnitAuthenticationOutcome.Rejected).diagnostic
            } +
                listOf(
                    (manifest as ManifestAuthenticationOutcome.Rejected).diagnostic,
                    (provider as UnitAuthenticationOutcome.Rejected).diagnostic,
                )
        }

        private fun source(
            confirmationValue: KeyConfirmationSnapshot = confirmation,
            pending: List<RecoveryQuarantineIntentRow> = emptyList(),
            inventory: List<RecoveryInventoryEntry> = emptyList(),
            reports: List<RecoveryReportOnlyInventoryEntry> = emptyList(),
        ) =
            object : RecoveryReconciliationSource {
                override fun loadConfirmation(runId: RunId) = confirmationValue

                override fun loadCandidate(runId: RunId) = candidate

                override fun loadArtifact(runId: RunId, relativeName: String) =
                    artifacts[relativeName]

                override fun loadPendingQuarantine(runId: RunId) = pending

                override fun loadInventory(runId: RunId) = inventory

                override fun loadInventorySnapshot(
                    runId: RunId,
                    candidate: RecoveryCandidateSnapshot,
                ) = RecoveryInventorySnapshot(inventory, reports)
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

        fun reconcileFinalOrphan(
            corrupt: Boolean,
            unsafe: Boolean = false,
            oversize: Boolean = false,
            cryptoThrows: Boolean = false,
        ): MicrofileReconciliationResult {
            val bytes =
                (if (oversize) ByteArray(513) else confirmationCiphertext.copyOf()).also {
                    if (corrupt) it[0] = (it[0].toInt() xor 1).toByte()
                }
            val orphanConfirmation =
                confirmation.copy(
                    durableRow = null,
                    finalArtifact =
                        ConfirmationArtifactSnapshot(
                            confirmation.finalArtifact!!.relativeName,
                            confirmation.finalArtifact.path.copy(containedBeneathRunRoot = !unsafe),
                            bytes,
                        ),
                )
            val qJournal = MemoryQuarantineJournal(quarantineEvents)
            val qStorage = MemoryQuarantineStorage(quarantineEvents)
            val orphanController =
                RecoveryMicrofileReconciliationController(
                    source(orphanConfirmation),
                    if (!cryptoThrows) crypto
                    else
                        object : RecoveryReconciliationCrypto by crypto {
                            override fun authenticateConfirmationOrphan(
                                expected: KeyConfirmationValue,
                                ciphertext: ByteArray,
                            ): KeyConfirmationDecryption = error("provider")
                        },
                    com.monumentogram.dora.poc.recovery.controller
                        .RecoveryKeyConfirmationController { runAead },
                    RecoveryQuarantineController(qStorage, qJournal) {
                        quarantineEvents += "evidence"
                    },
                )
            return orphanController.reconcile(run)
        }

        fun reconcilePendingAndInventory(): MicrofileReconciliationResult {
            fun input(name: String, value: Byte) =
                RecoveryQuarantineIntentInput(
                    RecoveryCandidate.MICROFILE,
                    run,
                    name,
                    RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
                    1UL,
                    Sha256Value.calculate(byteArrayOf(value)),
                )
            val first = input("stale.tmp", 7)
            val pending =
                RecoveryQuarantineIntentRow(
                    com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.calculate(
                        first
                    ),
                    first,
                    RecoveryQuarantineObservedState.TEMP_ONLY,
                    QuarantineBootstrapBinding.PRESENT,
                    com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
                        .destination(first),
                    QuarantineIntentState.PENDING,
                )
            val second = input("unknown.bin", 8)
            val journal = MemoryQuarantineJournal(quarantineEvents, pending)
            val quarantine =
                RecoveryQuarantineController(
                    MemoryQuarantineStorage(quarantineEvents),
                    journal,
                ) {
                    quarantineEvents += "evidence"
                }
            return RecoveryMicrofileReconciliationController(
                    source(
                        pending = listOf(pending),
                        inventory =
                            listOf(
                                RecoveryInventoryEntry(
                                    second,
                                    RecoveryQuarantineObservedState.FINAL_ORPHAN,
                                    QuarantineBootstrapBinding.PRESENT,
                                )
                            ),
                    ),
                    crypto,
                    com.monumentogram.dora.poc.recovery.controller
                        .RecoveryKeyConfirmationController { runAead },
                    quarantine,
                )
                .reconcile(run)
        }

        fun reconcileUnitFailure(
            diagnostic: RecoveryFailureDiagnostic
        ): MicrofileReconciliationResult.PartialPrefix {
            val rejecting =
                object : RecoveryReconciliationCrypto by crypto {
                    override fun authenticateUnit(
                        runId: RunId,
                        unit: RecoveryMicrofileUnitRow,
                        previousDigest: Sha256Value,
                        envelope: ByteArray,
                        ciphertext: ByteArray,
                    ) = UnitAuthenticationOutcome.Rejected(diagnostic)
                }
            return RecoveryMicrofileReconciliationController(
                    source(),
                    rejecting,
                    com.monumentogram.dora.poc.recovery.controller
                        .RecoveryKeyConfirmationController { runAead },
                )
                .reconcile(run) as MicrofileReconciliationResult.PartialPrefix
        }

        fun reconcileReportOnlyQuarantine(): MicrofileReconciliationResult {
            val reports =
                listOf(
                    RecoveryReportOnlyInventoryEntry(
                        "objects/q-${"1".repeat(64)}.bin",
                        QuarantinePathState.OCCUPIED,
                        1UL,
                        Sha256Value.calculate(byteArrayOf(1)),
                        false,
                    ),
                    RecoveryReportOnlyInventoryEntry(
                        "objects/unsafe",
                        QuarantinePathState.UNSAFE,
                        null,
                        null,
                        false,
                    ),
                )
            val quarantine =
                RecoveryQuarantineController(
                    MemoryQuarantineStorage(quarantineEvents),
                    MemoryQuarantineJournal(quarantineEvents),
                ) {
                    quarantineEvents += "evidence"
                }
            return RecoveryMicrofileReconciliationController(
                    source(reports = reports),
                    crypto,
                    com.monumentogram.dora.poc.recovery.controller
                        .RecoveryKeyConfirmationController { runAead },
                    quarantine,
                )
                .reconcile(run)
        }

        @Suppress("LongMethod")
        fun reconcileCorruptLatestManifest(): MicrofileReconciliationResult =
            reconcileLatestManifest(false)

        fun reconcileWrongChainLatestManifest(): MicrofileReconciliationResult =
            reconcileLatestManifest(true)

        @Suppress("LongMethod")
        private fun reconcileLatestManifest(wrongChain: Boolean): MicrofileReconciliationResult {
            val secondPlaintext = byteArrayOf(5, 6, 7, 8)
            val previousDigest = publication.publicationSha256
            val manifestPrevious =
                if (wrongChain) Sha256Value.calculate(byteArrayOf(44)) else previousDigest
            val unitAad2 =
                MicrofileAad(
                    RecoveryCandidate.MICROFILE,
                    run,
                    2UL,
                    1UL,
                    4UL,
                    8UL,
                    5UL,
                    previousDigest,
                )
            val envelopeAad2 =
                KeyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    run,
                    KeyEnvelopeTargetKind.MICROFILE,
                    2UL,
                    1UL,
                    4UL,
                    8UL,
                    5UL,
                    previousDigest,
                )
            val keyset2 = RecoveryTinkRuntime.newAeadKeyset(envelopeAad2)
            val envelope2 = keyset2.serializeEncrypted(runAead)
            val ciphertext2 = keyset2.encryptMicrofile(secondPlaintext, unitAad2)
            val row2 =
                RecoveryMicrofileUnitRow(
                    run.toCanonicalString(),
                    RecoveryCandidate.MICROFILE.contractId,
                    1UL,
                    4UL,
                    8UL,
                    5UL,
                    RecoveryRelativeNames.microfileCiphertext(1UL),
                    ciphertext2.size.toLong(),
                    Sha256Value.calculate(ciphertext2),
                    RecoveryRelativeNames.microfileKeyEnvelope(1UL),
                    envelope2.size.toLong(),
                    Sha256Value.calculate(envelope2),
                    2UL,
                    Sha256Value.calculate(byteArrayOf(10)),
                )
            val entries = listOf(entry, manifestEntryFor(row2))
            val manifest2 =
                RecoveryManifest.create(
                    RecoveryCandidate.MICROFILE,
                    run,
                    2UL,
                    manifestPrevious,
                    8UL,
                    entries,
                )
            val manifestEnvelopeAad2 =
                KeyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    run,
                    KeyEnvelopeTargetKind.MANIFEST,
                    2UL,
                    KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                    0UL,
                    8UL,
                    0UL,
                    manifestPrevious,
                )
            val manifestKeyset2 = RecoveryTinkRuntime.newAeadKeyset(manifestEnvelopeAad2)
            val manifestEnvelope2 = manifestKeyset2.serializeEncrypted(runAead)
            val publicationAad2 =
                PublicationAad(
                    RecoveryCandidate.MICROFILE,
                    run,
                    PublicationKind.MANIFEST,
                    2UL,
                    1UL,
                    8UL,
                    manifestPrevious,
                )
            val manifestCiphertext2 =
                manifestKeyset2
                    .encryptPublication(RecoveryManifestCodec.encode(manifest2), publicationAad2)
                    .also { if (!wrongChain) it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            val publication2 =
                RecoveryManifestPublicationRow(
                    run.toCanonicalString(),
                    RecoveryCandidate.MICROFILE.contractId,
                    PublicationKind.MANIFEST,
                    2UL,
                    8UL,
                    RecoveryRelativeNames.manifestCiphertext(2UL),
                    manifestCiphertext2.size.toLong(),
                    Sha256Value.calculate(manifestCiphertext2),
                    RecoveryRelativeNames.manifestKeyEnvelope(2UL),
                    manifestEnvelope2.size.toLong(),
                    Sha256Value.calculate(manifestEnvelope2),
                    manifestPrevious,
                )
            val snapshot =
                candidate.copy(
                    units = listOf(unitRow, row2),
                    publications = listOf(publication, publication2),
                )
            val allArtifacts =
                artifacts +
                    mapOf(
                        row2.keyEnvelopeRelativeName to
                            RecoveryArtifactBytes(row2.keyEnvelopeRelativeName, envelope2),
                        row2.ciphertextRelativeName to
                            RecoveryArtifactBytes(row2.ciphertextRelativeName, ciphertext2),
                        publication2.keyEnvelopeRelativeName to
                            RecoveryArtifactBytes(
                                publication2.keyEnvelopeRelativeName,
                                manifestEnvelope2,
                            ),
                        publication2.publicationRelativeName to
                            RecoveryArtifactBytes(
                                publication2.publicationRelativeName,
                                manifestCiphertext2,
                            ),
                    )
            return controllerFor(snapshot, allArtifacts).reconcile(run)
        }

        @Suppress("LongMethod")
        fun reconcileThreeUnitsWithAuthFailure(failureIndex: Int): MicrofileReconciliationResult {
            val rows = mutableListOf<RecoveryMicrofileUnitRow>()
            val publications = mutableListOf<RecoveryManifestPublicationRow>()
            val allArtifacts = mutableMapOf<String, RecoveryArtifactBytes>()
            var previousDigest = Sha256Value.ZERO
            repeat(3) { index ->
                val generation = (index + 1).toULong()
                val start = (index * 4).toULong()
                val end = start + 4UL
                val uAad =
                    MicrofileAad(
                        RecoveryCandidate.MICROFILE,
                        run,
                        generation,
                        index.toULong(),
                        start,
                        end,
                        5UL,
                        previousDigest,
                    )
                val eAad =
                    KeyEnvelopeAad(
                        RecoveryCandidate.MICROFILE,
                        run,
                        KeyEnvelopeTargetKind.MICROFILE,
                        generation,
                        index.toULong(),
                        start,
                        end,
                        5UL,
                        previousDigest,
                    )
                val keyset = RecoveryTinkRuntime.newAeadKeyset(eAad)
                val envelope = keyset.serializeEncrypted(runAead)
                val ciphertext =
                    keyset.encryptMicrofile(byteArrayOf(index.toByte(), 1, 2, 3), uAad).also {
                        if (index == failureIndex)
                            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
                    }
                val row =
                    RecoveryMicrofileUnitRow(
                        run.toCanonicalString(),
                        RecoveryCandidate.MICROFILE.contractId,
                        index.toULong(),
                        start,
                        end,
                        5UL,
                        RecoveryRelativeNames.microfileCiphertext(index.toULong()),
                        ciphertext.size.toLong(),
                        Sha256Value.calculate(ciphertext),
                        RecoveryRelativeNames.microfileKeyEnvelope(index.toULong()),
                        envelope.size.toLong(),
                        Sha256Value.calculate(envelope),
                        generation,
                        Sha256Value.calculate(byteArrayOf((20 + index).toByte())),
                    )
                rows += row
                allArtifacts[row.keyEnvelopeRelativeName] =
                    RecoveryArtifactBytes(row.keyEnvelopeRelativeName, envelope)
                allArtifacts[row.ciphertextRelativeName] =
                    RecoveryArtifactBytes(row.ciphertextRelativeName, ciphertext)
                val manifestN =
                    RecoveryManifest.create(
                        RecoveryCandidate.MICROFILE,
                        run,
                        generation,
                        previousDigest,
                        end,
                        rows.map(::manifestEntryFor),
                    )
                val meAad =
                    KeyEnvelopeAad(
                        RecoveryCandidate.MICROFILE,
                        run,
                        KeyEnvelopeTargetKind.MANIFEST,
                        generation,
                        KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                        0UL,
                        end,
                        0UL,
                        previousDigest,
                    )
                val manifestKeysetN = RecoveryTinkRuntime.newAeadKeyset(meAad)
                val manifestEnvelopeN = manifestKeysetN.serializeEncrypted(runAead)
                val pAad =
                    PublicationAad(
                        RecoveryCandidate.MICROFILE,
                        run,
                        PublicationKind.MANIFEST,
                        generation,
                        generation - 1UL,
                        end,
                        previousDigest,
                    )
                val manifestCiphertextN =
                    manifestKeysetN.encryptPublication(
                        RecoveryManifestCodec.encode(manifestN),
                        pAad,
                    )
                val pRow =
                    RecoveryManifestPublicationRow(
                        run.toCanonicalString(),
                        RecoveryCandidate.MICROFILE.contractId,
                        PublicationKind.MANIFEST,
                        generation,
                        end,
                        RecoveryRelativeNames.manifestCiphertext(generation),
                        manifestCiphertextN.size.toLong(),
                        Sha256Value.calculate(manifestCiphertextN),
                        RecoveryRelativeNames.manifestKeyEnvelope(generation),
                        manifestEnvelopeN.size.toLong(),
                        Sha256Value.calculate(manifestEnvelopeN),
                        previousDigest,
                    )
                publications += pRow
                allArtifacts[pRow.keyEnvelopeRelativeName] =
                    RecoveryArtifactBytes(pRow.keyEnvelopeRelativeName, manifestEnvelopeN)
                allArtifacts[pRow.publicationRelativeName] =
                    RecoveryArtifactBytes(pRow.publicationRelativeName, manifestCiphertextN)
                previousDigest = pRow.publicationSha256
            }
            return controllerFor(
                    candidate.copy(units = rows, publications = publications),
                    allArtifacts,
                )
                .reconcile(run)
        }

        @Suppress("LongMethod")
        fun reconcileUnitCount(count: Int): MicrofileReconciliationResult {
            val rows =
                (0 until count).map { index ->
                    RecoveryMicrofileUnitRow(
                        run.toCanonicalString(),
                        RecoveryCandidate.MICROFILE.contractId,
                        index.toULong(),
                        index.toULong(),
                        (index + 1).toULong(),
                        5UL,
                        RecoveryRelativeNames.microfileCiphertext(index.toULong()),
                        1L,
                        Sha256Value.calculate(byteArrayOf(1)),
                        RecoveryRelativeNames.microfileKeyEnvelope(index.toULong()),
                        1L,
                        Sha256Value.calculate(byteArrayOf(2)),
                        (index + 1).toULong(),
                        Sha256Value.calculate(byteArrayOf((index and 0xff).toByte())),
                    )
                }
            val admitted = rows.take(721)
            val publicationDigest = Sha256Value.calculate(byteArrayOf(3))
            val manifest721 =
                RecoveryManifest.create(
                    RecoveryCandidate.MICROFILE,
                    run,
                    721UL,
                    publicationDigest,
                    721UL,
                    admitted.map(::manifestEntryFor),
                )
            val publications =
                (1..721).map { generation ->
                    RecoveryManifestPublicationRow(
                        run.toCanonicalString(),
                        RecoveryCandidate.MICROFILE.contractId,
                        PublicationKind.MANIFEST,
                        generation.toULong(),
                        generation.toULong(),
                        RecoveryRelativeNames.manifestCiphertext(generation.toULong()),
                        1L,
                        publicationDigest,
                        RecoveryRelativeNames.manifestKeyEnvelope(generation.toULong()),
                        1L,
                        Sha256Value.calculate(byteArrayOf(4)),
                        if (generation == 1) Sha256Value.ZERO else publicationDigest,
                    )
                }
            val publication721 = publications.last()
            val values = buildMap {
                admitted.forEach { row ->
                    put(
                        row.ciphertextRelativeName,
                        RecoveryArtifactBytes(row.ciphertextRelativeName, byteArrayOf(1)),
                    )
                    put(
                        row.keyEnvelopeRelativeName,
                        RecoveryArtifactBytes(row.keyEnvelopeRelativeName, byteArrayOf(2)),
                    )
                }
                put(
                    publication721.publicationRelativeName,
                    RecoveryArtifactBytes(publication721.publicationRelativeName, byteArrayOf(3)),
                )
                put(
                    publication721.keyEnvelopeRelativeName,
                    RecoveryArtifactBytes(publication721.keyEnvelopeRelativeName, byteArrayOf(4)),
                )
            }
            val fakeCrypto =
                object : RecoveryReconciliationCrypto {
                    override fun authenticateConfirmationOrphan(
                        expected: KeyConfirmationValue,
                        ciphertext: ByteArray,
                    ) = KeyConfirmationDecryption.Success(expected)

                    override fun authenticateManifest(
                        runId: RunId,
                        publication: RecoveryManifestPublicationRow,
                        previousDigest: Sha256Value,
                        envelope: ByteArray,
                        ciphertext: ByteArray,
                    ) = ManifestAuthenticationOutcome.Authenticated(manifest721)

                    override fun authenticateUnit(
                        runId: RunId,
                        unit: RecoveryMicrofileUnitRow,
                        previousDigest: Sha256Value,
                        envelope: ByteArray,
                        ciphertext: ByteArray,
                    ) = UnitAuthenticationOutcome.Authenticated(byteArrayOf(0))
                }
            return RecoveryMicrofileReconciliationController(
                    object : RecoveryReconciliationSource {
                        override fun loadConfirmation(runId: RunId) = confirmation

                        override fun loadCandidate(runId: RunId) =
                            candidate.copy(units = rows, publications = publications)

                        override fun loadArtifact(runId: RunId, relativeName: String) =
                            values[relativeName]
                    },
                    fakeCrypto,
                    com.monumentogram.dora.poc.recovery.controller
                        .RecoveryKeyConfirmationController { runAead },
                )
                .reconcile(run)
        }

        private fun controllerFor(
            snapshot: RecoveryCandidateSnapshot,
            values: Map<String, RecoveryArtifactBytes>,
        ) =
            RecoveryMicrofileReconciliationController(
                object : RecoveryReconciliationSource {
                    override fun loadConfirmation(runId: RunId) = confirmation

                    override fun loadCandidate(runId: RunId) = snapshot

                    override fun loadArtifact(runId: RunId, relativeName: String) =
                        values[relativeName]
                },
                crypto,
                com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController {
                    runAead
                },
            )

        private fun manifestEntryFor(row: RecoveryMicrofileUnitRow) =
            RecoveryManifestEntry(
                row.unitIndex,
                row.plaintextStartInclusive,
                row.plaintextEndExclusive,
                row.cadenceSeconds,
                row.ciphertextBytes.toULong(),
                row.ciphertextSha256,
                row.keyEnvelopeBytes.toULong(),
                row.keyEnvelopeSha256,
                row.ciphertextRelativeName,
                row.keyEnvelopeRelativeName,
            )
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

    private class MemoryQuarantineJournal(
        private val events: MutableList<String>,
        initial: RecoveryQuarantineIntentRow? = null,
    ) : RecoveryQuarantineJournal {
        private var row: RecoveryQuarantineIntentRow? = initial

        override fun load(intentId: Sha256Value) = row?.takeIf { it.intentId == intentId }

        override fun loadBySource(input: RecoveryQuarantineIntentInput) = row?.takeIf {
            it.input.runId == input.runId &&
                it.input.sourceRelativeName == input.sourceRelativeName &&
                it.input.sourceSha256 == input.sourceSha256
        }

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
        ): ManifestAuthenticationOutcome {
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
            return ManifestAuthenticationOutcome.Authenticated(
                RecoveryManifestCodec.decode(keyset.decryptPublication(ciphertext, aad))
            )
        }

        override fun authenticateUnit(
            runId: RunId,
            unit: RecoveryMicrofileUnitRow,
            previousDigest: Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): UnitAuthenticationOutcome {
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
            return try {
                UnitAuthenticationOutcome.Authenticated(keyset.decryptMicrofile(ciphertext, aad))
            } catch (error: GeneralSecurityException) {
                UnitAuthenticationOutcome.Rejected(
                    RecoveryFailureDiagnostic.capture(
                        RecoveryFailureCategory.AUTHENTICATION_REJECTED,
                        error,
                        RecoveryFailureStage.UNIT_PAYLOAD_DECRYPT,
                    )
                )
            }
        }
    }
}
