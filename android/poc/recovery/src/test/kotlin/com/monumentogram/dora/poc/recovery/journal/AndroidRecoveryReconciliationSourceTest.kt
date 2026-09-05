package com.monumentogram.dora.poc.recovery.journal

import android.database.Cursor
import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.candidate.AndroidRecoveryMicrofileReconciliation
import com.monumentogram.dora.poc.recovery.candidate.CandidateBootstrapRow
import com.monumentogram.dora.poc.recovery.candidate.ManifestAuthenticationOutcome
import com.monumentogram.dora.poc.recovery.candidate.MicrofileReconciliationResult
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.ReconciliationDiagnostic
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureStage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryManifestPublicationRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileReconciliationController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineEvidenceSink
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationCrypto
import com.monumentogram.dora.poc.recovery.candidate.RecoverySourceAccessException
import com.monumentogram.dora.poc.recovery.candidate.UnitAuthenticationOutcome
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecordingRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactRoleBounds
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationDescriptor
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationOs
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationStat
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRecoveryReconciliationSourceTest {
    @Test
    fun `bootstrap cursor decoder returns null for zero rows`() {
        val probe = CursorProbe(emptyList())

        assertEquals(
            null,
            AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(probe.cursor, RUN),
        )
        assertEquals(0, probe.getterPositions.size)
    }

    @Test
    fun `bootstrap cursor decoder decodes the one stored identity at position zero`() {
        val row = bootstrapRow()
        val probe = CursorProbe(listOf(row))

        val identity =
            AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(probe.cursor, RUN)

        assertEquals(expectedBootstrapIdentity(row), identity)
        assertEquals(listOf(0, 0, 0, 0, 0), probe.getterPositions)
        assertEquals(0, probe.advanceCount)
    }

    @Test
    fun `bootstrap cursor decoder rejects two rows as a structural journal failure`() {
        val probe = CursorProbe(listOf(bootstrapRow(), bootstrapRow()))

        val failure = assertThrowsSource {
            AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(probe.cursor, RUN)
        }

        assertEquals(RecoveryFailureCategory.STRUCTURAL, failure.diagnostic.category)
        assertEquals(RecoveryFailureStage.JOURNAL, failure.diagnostic.stage)
        assertEquals(0, probe.getterPositions.size)
    }

    @Test
    fun `actual source treats never-created quarantine namespace as empty`() {
        val os =
            InventoryOs().apply {
                seed(emptyMap(), emptyMap())
                removeQuarantineNamespace()
            }
        val inventory =
            source(os)
                .loadInventorySnapshot(
                    RUN,
                    RecoveryCandidateSnapshot(emptyList(), emptyList(), emptyList()),
                )
        assertTrue(inventory.quarantine.isEmpty())
    }

    @Test
    @Suppress("LongMethod")
    fun `actual source reports active taxonomy and pending completed and unreferenced quarantine`() {
        val os = InventoryOs()
        val pending = intent("units/u-0.bin", byteArrayOf(1), QuarantineIntentState.PENDING)
        val completed = intent("units/u-1.bin", byteArrayOf(2), QuarantineIntentState.COMPLETED)
        os.seed(
            mapOf(
                "units/u-0.bin.tmp" to byteArrayOf(3),
                "units/u-0.bin" to byteArrayOf(4),
                "units/u-1.bin.tmp" to byteArrayOf(7),
                "units/u-2.bin" to byteArrayOf(8),
                "unknown.bin" to byteArrayOf(5),
            ),
            mapOf(
                pending.destinationRelativeName to byteArrayOf(1),
                completed.destinationRelativeName to byteArrayOf(2),
                "objects/q-${"f".repeat(64)}.bin" to byteArrayOf(6),
            ),
        )
        os.addUnsafeQuarantine("objects/nested")
        val source =
            AndroidRecoveryReconciliationSource(
                loadBootstrap = { null },
                loadSnapshot = {
                    RecoveryCandidateSnapshot(emptyList(), listOf(tempReferenceRow()), emptyList())
                },
                loadPending = { listOf(pending) },
                loadAllIntents = { listOf(pending, completed) },
                storage = AndroidOsRecoveryReconciliationStorage(ROOT, os),
                aliasExists = { false },
            )
        val inventory =
            source.loadInventorySnapshot(
                RUN,
                RecoveryCandidateSnapshot(emptyList(), listOf(tempReferenceRow()), emptyList()),
            )
        assertEquals(5, inventory.active.size)
        assertEquals(
            RecoveryQuarantineObservedState.TEMP_AND_FINAL,
            inventory.active
                .single { it.input.sourceRelativeName == "units/u-0.bin.tmp" }
                .observedState,
        )
        assertEquals(
            RecoveryQuarantineObservedState.UNKNOWN_OR_NON_ALLOWLISTED_NAME,
            inventory.active.single { it.input.sourceRelativeName == "unknown.bin" }.observedState,
        )
        assertEquals(
            RecoveryQuarantineObservedState.SQLITE_POINTS_TO_TEMP,
            inventory.active
                .single { it.input.sourceRelativeName == "units/u-1.bin.tmp" }
                .observedState,
        )
        assertEquals(
            RecoveryQuarantineObservedState.FINAL_ORPHAN,
            inventory.active
                .single { it.input.sourceRelativeName == "units/u-2.bin" }
                .observedState,
        )
        assertEquals(4, inventory.quarantine.size)
        assertEquals(2, inventory.quarantine.count { it.knownIntentDestination })
        assertTrue(
            inventory.quarantine.any {
                !it.knownIntentDestination && it.pathState == QuarantinePathState.OCCUPIED
            }
        )
        assertTrue(inventory.quarantine.any { it.pathState == QuarantinePathState.UNSAFE })
        assertFalse(os.listed.any { it.endsWith("nested") })
    }

    @Test
    fun `inventory names receive all exact active artifact roles`() {
        val cases =
            mapOf(
                "key-confirmation/run.kc" to RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                "units/u-0.bin" to RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                "manifests/m-1.bin" to RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT,
                "key-envelopes/manifest-1.bin" to
                    RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE,
                "key-envelopes/unit-0.bin" to RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE,
                "unknown.bin" to RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
            )
        cases.forEach { (name, expected) ->
            assertEquals(expected, RecoveryInventoryClassifier.role(name))
        }
    }

    @Test
    fun `production composition exposes no caller supplied snapshot or source parameter`() {
        val create =
            AndroidRecoveryMicrofileReconciliation::class.java.declaredMethods.single {
                it.name == "create"
            }
        assertEquals(2, create.parameterCount)
        assertFalse(
            create.parameterTypes.any {
                it.name.contains("Snapshot") || it.name.contains("ReconciliationSource")
            }
        )
        assertEquals(RecoveryQuarantineEvidenceSink::class.java, create.parameterTypes.last())
    }

    @Test
    fun `actual source keeps journal alias path and descriptor failures distinct`() {
        val os = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        val journalFailure = source(os, loadSnapshot = { error("sqlite") })
        val journal = assertThrowsSource { journalFailure.loadCandidate(RUN) }
        assertEquals(RecoveryFailureStage.JOURNAL, journal.diagnostic.stage)
        val structural =
            source(
                os,
                loadSnapshot = {
                    throw RecoverySourceAccessException(
                        com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureDiagnostic(
                            RecoveryFailureCategory.STRUCTURAL,
                            "MalformedRow",
                            "decoded row identity mismatch",
                            RecoveryFailureStage.JOURNAL,
                        ),
                        IllegalStateException("malformed"),
                    )
                },
            )
        assertEquals(
            RecoveryFailureCategory.STRUCTURAL,
            assertThrowsSource { structural.loadCandidate(RUN) }.diagnostic.category,
        )

        val absentAlias = source(os, alias = { false }).loadConfirmation(RUN)
        assertEquals(
            com.monumentogram.dora.poc.recovery.controller.AliasObservation.ABSENT,
            absentAlias.alias,
        )
        val alias = assertThrowsSource {
            source(os, alias = { error("provider") }).loadConfirmation(RUN)
        }
        assertEquals(RecoveryFailureStage.ALIAS_OBSERVATION, alias.diagnostic.stage)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, alias.diagnostic.category)

        os.addActive("units/u-0.bin", byteArrayOf(1))
        os.unsafe("units")
        val unsafe = assertThrowsSource {
            source(os)
                .loadArtifact(
                    RUN,
                    "units/u-0.bin",
                    RecoveryArtifactContext.UNIT_CIPHERTEXT,
                )
        }
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, unsafe.diagnostic.stage)
        os.safeDirectory("units")
        os.failRead = true
        val io = assertThrowsSource {
            source(os)
                .loadArtifact(
                    RUN,
                    "units/u-0.bin",
                    RecoveryArtifactContext.UNIT_CIPHERTEXT,
                )
        }
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, io.diagnostic.stage)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, io.diagnostic.category)
    }

    @Test
    fun `actual controller keeps no-row and durable-row confirmation path classifications`() {
        val noRowOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        noRowOs.addActive("key-confirmation/run.kc", byteArrayOf(1))
        noRowOs.setType("key-confirmation/run.kc", BootstrapPathType.SYMLINK)
        val noRow =
            reconcile(source(noRowOs)) as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP, noRow.classification)
        assertEquals(ReconciliationDiagnostic.UNSAFE_PATH, noRow.diagnostic)
        assertEquals(RecoveryFailureCategory.CORRUPT_LEAF, noRow.failure?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, noRow.failure?.stage)

        val durableOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        durableOs.addActive("key-confirmation/run.kc", byteArrayOf(1))
        durableOs.setType("key-confirmation/run.kc", BootstrapPathType.SYMLINK)
        val durableCursor = CursorProbe(listOf(bootstrapRow()))
        val durable =
            reconcile(
                source(
                    durableOs,
                    loadBootstrap = {
                        AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(
                            durableCursor.cursor,
                            RUN,
                        )
                    },
                )
            )
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION, durable.classification)
        assertEquals(ReconciliationDiagnostic.UNSAFE_PATH, durable.diagnostic)
        assertEquals(RecoveryFailureCategory.CORRUPT_LEAF, durable.failure?.category)
        assertEquals(listOf(0, 0, 0, 0, 0), durableCursor.getterPositions)
    }

    @Test
    fun `actual controller distinguishes known final from unknown and temporary observation`() {
        val oversizedOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        oversizedOs.addActive("key-confirmation/run.kc", byteArrayOf(1))
        oversizedOs.setSize("key-confirmation/run.kc", 513)
        val oversized =
            reconcile(source(oversizedOs)) as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP, oversized.classification)
        assertEquals(RecoveryFailureCategory.STRUCTURAL, oversized.failure?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, oversized.failure?.stage)

        val unknownOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        unknownOs.failLstat("key-confirmation/run.kc")
        val unknown =
            reconcile(source(unknownOs)) as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(null, unknown.classification)
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, unknown.failure?.stage)

        val tempOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        tempOs.addActive("key-confirmation/run.kc", byteArrayOf(1))
        tempOs.setType("key-confirmation/run.kc.tmp", BootstrapPathType.SYMLINK)
        val temp = reconcile(source(tempOs)) as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP, temp.classification)
        assertEquals(ReconciliationDiagnostic.UNSAFE_PATH, temp.diagnostic)

        val missingFinalOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        missingFinalOs.failLstat("key-confirmation/run.kc.tmp")
        val missingFinal =
            reconcile(
                source(
                    missingFinalOs,
                    loadBootstrap = { expectedBootstrapIdentity(bootstrapRow()) },
                )
            )
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(
            KeyRecoveryClassification.KEY_CONFIRMATION_MISSING,
            missingFinal.classification,
        )
        assertEquals(RecoveryFailureCategory.OPERATIONAL, missingFinal.failure?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, missingFinal.failure?.stage)

        val absentBothOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        absentBothOs.failLstat("key-confirmation/run.kc.tmp")
        val absentBoth =
            reconcile(source(absentBothOs)) as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(null, absentBoth.classification)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, absentBoth.failure?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, absentBoth.failure?.stage)
    }

    @Test
    fun `actual source retains key envelope structural role separately from ciphertext`() {
        val os = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        os.addActive("key-envelopes/unit-0.bin", byteArrayOf(1))
        os.setSize("key-envelopes/unit-0.bin", 65_537)
        val envelope = assertThrowsSource {
            source(os)
                .loadArtifact(
                    RUN,
                    "key-envelopes/unit-0.bin",
                    RecoveryArtifactContext.UNIT_KEY_ENVELOPE,
                )
        }
        assertEquals(RecoveryFailureCategory.STRUCTURAL, envelope.diagnostic.category)
        assertEquals(RecoveryFailureStage.ENVELOPE_BINDING, envelope.diagnostic.stage)

        os.addActive("units/u-0.bin", byteArrayOf(1))
        os.setSize("units/u-0.bin", 960_257)
        val ciphertext = assertThrowsSource {
            source(os)
                .loadArtifact(
                    RUN,
                    "units/u-0.bin",
                    RecoveryArtifactContext.UNIT_CIPHERTEXT,
                )
        }
        assertEquals(RecoveryFailureCategory.STRUCTURAL, ciphertext.diagnostic.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, ciphertext.diagnostic.stage)
    }

    @Test
    fun `actual controller maps manifest envelope and ciphertext structural reads by caller role`() {
        listOf(
                RecoveryArtifactCase.MANIFEST_ENVELOPE to
                    KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE,
                RecoveryArtifactCase.MANIFEST_CIPHERTEXT to null,
            )
            .forEach { (artifactCase, expectedClassification) ->
                val fixture = LaterArtifactFixture(artifactCase)
                val result =
                    fixture.reconcile() as MicrofileReconciliationResult.NoAuthenticatedPrefix
                val rejection = result.manifestRejections.first()
                assertEquals(2UL, rejection.generation)
                assertEquals(expectedClassification, rejection.classification)
                assertEquals(
                    ReconciliationDiagnostic.MANIFEST_MISSING_OR_INVALID,
                    result.diagnostic,
                )
                assertEquals(
                    if (artifactCase == RecoveryArtifactCase.MANIFEST_ENVELOPE)
                        RecoveryFailureStage.ENVELOPE_BINDING
                    else RecoveryFailureStage.ARTIFACT_PATH,
                    rejection.diagnostic.stage,
                )
                assertEquals(RecoveryFailureCategory.STRUCTURAL, rejection.diagnostic.category)
                assertEquals(listOf(2UL, 1UL), result.manifestRejections.map { it.generation })
                assertManifestStopsAfterTargetRead(fixture)
            }
    }

    @Test
    fun `actual controller maps unit envelope structural read without relabeling ciphertext`() {
        listOf(
                RecoveryArtifactCase.UNIT_ENVELOPE to
                    KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE,
                RecoveryArtifactCase.UNIT_CIPHERTEXT to null,
            )
            .forEach { (artifactCase, expectedClassification) ->
                val fixture = LaterArtifactFixture(artifactCase)
                val result = fixture.reconcile() as MicrofileReconciliationResult.PartialPrefix
                assertEquals(expectedClassification, result.classification)
                assertEquals(ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID, result.diagnostic)
                assertEquals(
                    if (artifactCase == RecoveryArtifactCase.UNIT_ENVELOPE)
                        RecoveryFailureStage.ENVELOPE_BINDING
                    else RecoveryFailureStage.ARTIFACT_PATH,
                    result.failure?.stage,
                )
                assertEquals(RecoveryFailureCategory.STRUCTURAL, result.failure?.category)
                assertEquals(1, result.prefix.units.size)
                assertEquals(1UL, result.prefix.authenticatedEndExclusive)
                assertTrue(result.capability.authorizes(result.prefix))
                assertTrue("crypto.unit:0" in fixture.eventsSnapshot())
                assertFalse("crypto.unit:1" in fixture.eventsSnapshot())
                assertTrue(fixture.eventsSnapshot().last().startsWith(fixture.targetClosePrefix()))
            }
    }

    @Test
    fun `actual controller keeps later syscall IO operational across artifact roles`() {
        RecoveryArtifactCase.entries.forEach { artifactCase ->
            val fixture = LaterArtifactFixture(artifactCase, operational = true)
            val result = fixture.reconcile()
            when (result) {
                is MicrofileReconciliationResult.NoAuthenticatedPrefix -> {
                    assertEquals(null, result.classification)
                    assertEquals(ReconciliationDiagnostic.CRYPTO_OPERATIONAL, result.diagnostic)
                    assertEquals(RecoveryFailureCategory.OPERATIONAL, result.failure?.category)
                    assertEquals(RecoveryFailureStage.ARTIFACT_IO, result.failure?.stage)
                    assertEquals(2UL, result.manifestRejections.first().generation)
                    assertManifestStopsAfterTargetRead(fixture)
                }
                is MicrofileReconciliationResult.PartialPrefix -> {
                    assertEquals(null, result.classification)
                    assertEquals(ReconciliationDiagnostic.CRYPTO_OPERATIONAL, result.diagnostic)
                    assertEquals(RecoveryFailureCategory.OPERATIONAL, result.failure?.category)
                    assertEquals(RecoveryFailureStage.ARTIFACT_IO, result.failure?.stage)
                    assertEquals(1, result.prefix.units.size)
                    assertEquals(1UL, result.prefix.authenticatedEndExclusive)
                    assertTrue("crypto.unit:0" in fixture.eventsSnapshot())
                    assertFalse("crypto.unit:1" in fixture.eventsSnapshot())
                    assertTrue(
                        fixture.eventsSnapshot().last().startsWith(fixture.targetClosePrefix())
                    )
                }
                else -> error("unexpected result $result")
            }
        }
    }

    private fun assertManifestStopsAfterTargetRead(fixture: LaterArtifactFixture) {
        val events = fixture.eventsSnapshot()
        val targetClose = events.indexOfLast { it.startsWith(fixture.targetClosePrefix()) }
        assertTrue(targetClose >= 0)
        assertTrue(events.drop(targetClose + 1).all { it.startsWith("os.lstat:") })
        assertFalse(events.any { it.startsWith("crypto.manifest:") })
    }

    @Test
    fun `actual controller retains unknown bootstrap query and cursor failures before artifact access`() {
        val queryOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        val query =
            reconcile(source(queryOs, loadBootstrap = { error("query") }))
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(null, query.classification)
        assertEquals(ReconciliationDiagnostic.CRYPTO_OPERATIONAL, query.diagnostic)
        assertEquals(RecoveryFailureStage.JOURNAL, query.failure?.stage)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, query.failure?.category)
        assertEquals(listOf("journal.bootstrap"), queryOs.events)

        val cursorOs = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        val cursor = CursorProbe(listOf(bootstrapRow(), bootstrapRow()))
        val duplicate =
            reconcile(
                source(
                    cursorOs,
                    loadBootstrap = {
                        AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(
                            cursor.cursor,
                            RUN,
                        )
                    },
                )
            )
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(null, duplicate.classification)
        assertEquals(ReconciliationDiagnostic.INVALID_BOOTSTRAP_ROOT, duplicate.diagnostic)
        assertEquals(RecoveryFailureStage.JOURNAL, duplicate.failure?.stage)
        assertEquals(RecoveryFailureCategory.STRUCTURAL, duplicate.failure?.category)
        assertEquals(listOf("journal.bootstrap"), cursorOs.events)
    }

    @Test
    fun `actual controller maps confirmation final descriptor controls by durable row state`() {
        FinalControl.entries.forEach { control ->
            listOf(false, true).forEach { durable ->
                val bytes = if (control == FinalControl.EMPTY) ByteArray(0) else byteArrayOf(1)
                val os = InventoryOs().apply { seed(mapOf(CONFIRMATION_NAME to bytes), emptyMap()) }
                control.configure(os)
                val row = if (durable) storedConfirmation(bytes) else null
                val result =
                    reconcile(source(os, loadBootstrap = { row }))
                        as MicrofileReconciliationResult.NoAuthenticatedPrefix
                assertEquals(control.expectedClassification(durable), result.classification)
                assertEquals(control.publicDiagnostic, result.diagnostic)
                assertEquals(control.category, result.failure?.category)
                assertEquals(control.stage, result.failure?.stage)
                assertFalse(
                    os.events.any { it == "alias.observe" || it.startsWith("journal.snapshot") }
                )
                assertEquals(control.expectedLastEvent, os.events.last())
            }
        }
    }

    @Test
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun `actual controller maps all confirmation temp row and final presence combinations`() {
        listOf(false, true).forEach { durable ->
            listOf(false, true).forEach { finalPresent ->
                listOf(false, true).forEach { unsafe ->
                    val bytes = byteArrayOf(1)
                    val os =
                        InventoryOs().apply {
                            seed(
                                if (finalPresent) mapOf(CONFIRMATION_NAME to bytes) else emptyMap(),
                                emptyMap(),
                            )
                            if (unsafe) setType(CONFIRMATION_TEMP_NAME, BootstrapPathType.SYMLINK)
                            else failLstat(CONFIRMATION_TEMP_NAME)
                        }
                    val result =
                        reconcile(
                            source(
                                os,
                                loadBootstrap = {
                                    if (durable) storedConfirmation(bytes) else null
                                },
                            )
                        )
                            as MicrofileReconciliationResult.NoAuthenticatedPrefix
                    val expectedClassification =
                        when {
                            durable && !finalPresent ->
                                KeyRecoveryClassification.KEY_CONFIRMATION_MISSING
                            !durable && (finalPresent || unsafe) ->
                                KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP
                            else -> null
                        }
                    assertEquals(expectedClassification, result.classification)
                    assertEquals(
                        if (unsafe) ReconciliationDiagnostic.UNSAFE_PATH
                        else ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
                        result.diagnostic,
                    )
                    assertEquals(
                        if (unsafe) RecoveryFailureCategory.CORRUPT_LEAF
                        else RecoveryFailureCategory.OPERATIONAL,
                        result.failure?.category,
                    )
                    assertEquals(
                        if (unsafe) RecoveryFailureStage.ARTIFACT_PATH
                        else RecoveryFailureStage.ARTIFACT_IO,
                        result.failure?.stage,
                    )
                    assertEquals("os.lstat:$CONFIRMATION_TEMP_NAME", os.events.last())
                    assertFalse(os.events.any { it == "alias.observe" || it == "journal.snapshot" })
                }
            }
        }
    }

    private fun source(
        os: InventoryOs,
        loadBootstrap: (RunId) -> StoredKeyConfirmationIdentity? = { null },
        loadSnapshot: (RunId) -> RecoveryCandidateSnapshot = {
            RecoveryCandidateSnapshot(emptyList(), emptyList(), emptyList())
        },
        alias: (RunId) -> Boolean = { false },
        ledger: MutableList<String> = os.events,
    ) =
        AndroidRecoveryReconciliationSource(
            loadBootstrap = {
                ledger += "journal.bootstrap"
                loadBootstrap(it)
            },
            loadSnapshot = {
                ledger += "journal.snapshot"
                loadSnapshot(it)
            },
            loadPending = {
                ledger += "journal.pending"
                emptyList()
            },
            loadAllIntents = {
                ledger += "journal.allIntents"
                emptyList()
            },
            storage = AndroidOsRecoveryReconciliationStorage(ROOT, os),
            aliasExists = {
                ledger += "alias.observe"
                alias(it)
            },
        )

    private fun reconcile(source: AndroidRecoveryReconciliationSource) =
        RecoveryMicrofileReconciliationController(source, RejectingCrypto).reconcile(RUN)

    private object RejectingCrypto : RecoveryReconciliationCrypto {
        override fun authenticateConfirmationOrphan(
            expected: KeyConfirmationValue,
            ciphertext: ByteArray,
        ): KeyConfirmationDecryption = error("must not authenticate")

        override fun authenticateManifest(
            runId: RunId,
            publication: RecoveryManifestPublicationRow,
            previousDigest: Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): ManifestAuthenticationOutcome = error("must not authenticate")

        override fun authenticateUnit(
            runId: RunId,
            unit: RecoveryMicrofileUnitRow,
            previousDigest: Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): UnitAuthenticationOutcome = error("must not authenticate")
    }

    private enum class RecoveryArtifactCase {
        MANIFEST_ENVELOPE,
        MANIFEST_CIPHERTEXT,
        UNIT_ENVELOPE,
        UNIT_CIPHERTEXT,
    }

    @Suppress("LongParameterList")
    private enum class FinalControl(
        val fault: DescriptorFault?,
        val category: RecoveryFailureCategory,
        val stage: RecoveryFailureStage,
        val publicDiagnostic: ReconciliationDiagnostic?,
        val expectedLastEvent: String,
    ) {
        INITIAL_UNSAFE(
            null,
            RecoveryFailureCategory.CORRUPT_LEAF,
            RecoveryFailureStage.ARTIFACT_PATH,
            ReconciliationDiagnostic.UNSAFE_PATH,
            "os.lstat:$CONFIRMATION_NAME",
        ),
        INITIAL_LSTAT_IO(
            null,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.lstat:$CONFIRMATION_NAME",
        ),
        EMPTY(
            null,
            RecoveryFailureCategory.STRUCTURAL,
            RecoveryFailureStage.ARTIFACT_PATH,
            null,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        OPEN(
            DescriptorFault.OPEN,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.open:$CONFIRMATION_NAME",
        ),
        FSTAT_IO(
            DescriptorFault.FSTAT_THROW,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        FSTAT_NONREGULAR(
            DescriptorFault.FSTAT_NONREGULAR,
            RecoveryFailureCategory.CORRUPT_LEAF,
            RecoveryFailureStage.ARTIFACT_PATH,
            ReconciliationDiagnostic.UNSAFE_PATH,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        FSTAT_NEGATIVE(
            DescriptorFault.FSTAT_NEGATIVE_SIZE,
            RecoveryFailureCategory.STRUCTURAL,
            RecoveryFailureStage.ARTIFACT_PATH,
            null,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        FSTAT_OVERSIZE(
            DescriptorFault.FSTAT_OVERSIZE,
            RecoveryFailureCategory.STRUCTURAL,
            RecoveryFailureStage.ARTIFACT_PATH,
            null,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        READ_IO(
            DescriptorFault.READ_THROW,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        PREMATURE_EOF(
            DescriptorFault.PREMATURE_EOF,
            RecoveryFailureCategory.STRUCTURAL,
            RecoveryFailureStage.ARTIFACT_PATH,
            null,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        GROWTH(
            DescriptorFault.GROWTH,
            RecoveryFailureCategory.STRUCTURAL,
            RecoveryFailureStage.ARTIFACT_PATH,
            null,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        NEGATIVE_READ(
            DescriptorFault.NEGATIVE_READ,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        OVERCOUNT_READ(
            DescriptorFault.OVERCOUNT_READ,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        CLOSE(
            DescriptorFault.CLOSE,
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ARTIFACT_IO,
            ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
            "os.close:$CONFIRMATION_NAME:1",
        ),
        PREMATURE_EOF_AND_CLOSE(
            DescriptorFault.PREMATURE_EOF_AND_CLOSE,
            RecoveryFailureCategory.STRUCTURAL,
            RecoveryFailureStage.ARTIFACT_PATH,
            null,
            "os.close:$CONFIRMATION_NAME:1",
        );

        fun configure(os: InventoryOs) {
            when (this) {
                INITIAL_UNSAFE -> os.setType(CONFIRMATION_NAME, BootstrapPathType.SYMLINK)
                INITIAL_LSTAT_IO -> os.failLstat(CONFIRMATION_NAME)
                EMPTY -> Unit
                else -> os.descriptorFault(CONFIRMATION_NAME, requireNotNull(fault))
            }
        }

        fun expectedClassification(durable: Boolean): KeyRecoveryClassification? =
            when {
                !durable && this == INITIAL_LSTAT_IO -> null
                !durable -> KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP
                category in
                    setOf(
                        RecoveryFailureCategory.CORRUPT_LEAF,
                        RecoveryFailureCategory.STRUCTURAL,
                    ) -> KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION
                else -> null
            }
    }

    private class LaterArtifactFixture(
        private val failing: RecoveryArtifactCase,
        operational: Boolean = false,
    ) {
        private val confirmationBackend = RecordingRunAeadBackend()
        private val runAead = RecoveryRunAeadProvider(confirmationBackend).openExisting(RUN)
        private val confirmationValue = KeyConfirmationValue(RecoveryCandidate.MICROFILE, RUN)
        private val confirmationBytes = runAead.encryptKeyConfirmation(confirmationValue)
        private val unitCiphertexts = listOf(byteArrayOf(1), byteArrayOf(5))
        private val unitEnvelopes = listOf(byteArrayOf(2), byteArrayOf(6))
        private val manifestCiphertext = byteArrayOf(3)
        private val manifestEnvelope = byteArrayOf(4)
        private val units = List(2) { unit(it) }
        private val priorManifestCiphertext = byteArrayOf(7)
        private val priorManifestEnvelope = byteArrayOf(8)
        private val priorPublication =
            publication(1UL, priorManifestCiphertext, priorManifestEnvelope)
        private val publication =
            publication(
                2UL,
                manifestCiphertext,
                manifestEnvelope,
                priorPublication.publicationSha256,
            )
        private val manifest =
            RecoveryManifest.create(
                RecoveryCandidate.MICROFILE,
                RUN,
                2UL,
                priorPublication.publicationSha256,
                2UL,
                units.mapIndexed { index, row ->
                    RecoveryManifestEntry(
                        row.unitIndex,
                        row.plaintextStartInclusive,
                        row.plaintextEndExclusive,
                        row.cadenceSeconds,
                        unitCiphertexts[index].size.toULong(),
                        Sha256Value.calculate(unitCiphertexts[index]),
                        unitEnvelopes[index].size.toULong(),
                        Sha256Value.calculate(unitEnvelopes[index]),
                        row.ciphertextRelativeName,
                        row.keyEnvelopeRelativeName,
                    )
                },
            )
        private val targetRelativeName =
            when (failing) {
                RecoveryArtifactCase.MANIFEST_ENVELOPE -> publication.keyEnvelopeRelativeName
                RecoveryArtifactCase.MANIFEST_CIPHERTEXT -> publication.publicationRelativeName
                RecoveryArtifactCase.UNIT_ENVELOPE -> units[1].keyEnvelopeRelativeName
                RecoveryArtifactCase.UNIT_CIPHERTEXT -> units[1].ciphertextRelativeName
            }
        private val candidate =
            RecoveryCandidateSnapshot(
                listOf(
                    CandidateBootstrapRow(
                        RUN.toCanonicalString(),
                        RecoveryCandidate.MICROFILE.contractId,
                        KeyConfirmationState.VALID,
                    )
                ),
                units,
                listOf(priorPublication, publication),
            )
        private val os =
            InventoryOs().apply {
                seed(
                    mapOf(
                        "key-confirmation/run.kc" to confirmationBytes,
                        units[0].ciphertextRelativeName to unitCiphertexts[0],
                        units[0].keyEnvelopeRelativeName to unitEnvelopes[0],
                        units[1].ciphertextRelativeName to unitCiphertexts[1],
                        units[1].keyEnvelopeRelativeName to unitEnvelopes[1],
                        publication.publicationRelativeName to manifestCiphertext,
                        publication.keyEnvelopeRelativeName to manifestEnvelope,
                    ),
                    emptyMap(),
                )
                if (operational) ioOnSecondRead(targetRelativeName)
                else structuralOnSecondRead(targetRelativeName)
            }
        private val source =
            AndroidRecoveryReconciliationSource(
                loadBootstrap = {
                    os.events += "journal.bootstrap"
                    StoredKeyConfirmationIdentity(
                        confirmationValue,
                        "key-confirmation/run.kc",
                        confirmationBytes.size.toLong(),
                        Sha256Value.calculate(confirmationBytes),
                        confirmationValue.canonicalAliasSha256,
                    )
                },
                loadSnapshot = {
                    os.events += "journal.snapshot"
                    candidate
                },
                loadPending = {
                    os.events += "journal.pending"
                    emptyList()
                },
                loadAllIntents = {
                    os.events += "journal.allIntents"
                    emptyList()
                },
                storage = AndroidOsRecoveryReconciliationStorage(ROOT, os),
                aliasExists = {
                    os.events += "alias.observe"
                    true
                },
            )
        private val crypto =
            object : RecoveryReconciliationCrypto {
                override fun authenticateConfirmationOrphan(
                    expected: KeyConfirmationValue,
                    ciphertext: ByteArray,
                ): KeyConfirmationDecryption = error("not used")

                override fun authenticateManifest(
                    runId: RunId,
                    publication: RecoveryManifestPublicationRow,
                    previousDigest: Sha256Value,
                    envelope: ByteArray,
                    ciphertext: ByteArray,
                ): ManifestAuthenticationOutcome {
                    os.events += "crypto.manifest:${publication.generation}"
                    return ManifestAuthenticationOutcome.Authenticated(manifest)
                }

                override fun authenticateUnit(
                    runId: RunId,
                    unit: RecoveryMicrofileUnitRow,
                    previousDigest: Sha256Value,
                    envelope: ByteArray,
                    ciphertext: ByteArray,
                ): UnitAuthenticationOutcome {
                    os.events += "crypto.unit:${unit.unitIndex}"
                    return UnitAuthenticationOutcome.Authenticated(byteArrayOf(9))
                }
            }

        fun reconcile() =
            RecoveryMicrofileReconciliationController(
                    source,
                    crypto,
                    RecoveryKeyConfirmationController(
                        RecoveryRunAeadProvider(confirmationBackend)::openExisting
                    ),
                )
                .reconcile(RUN)

        fun eventsSnapshot(): List<String> = os.events.toList()

        fun targetClosePrefix(): String = "os.close:$targetRelativeName:"

        private fun unit(index: Int): RecoveryMicrofileUnitRow {
            val unitIndex = index.toULong()
            return RecoveryMicrofileUnitRow(
                RUN.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                unitIndex,
                unitIndex,
                unitIndex + 1UL,
                5UL,
                RecoveryRelativeNames.microfileCiphertext(unitIndex),
                unitCiphertexts[index].size.toLong(),
                Sha256Value.calculate(unitCiphertexts[index]),
                RecoveryRelativeNames.microfileKeyEnvelope(unitIndex),
                unitEnvelopes[index].size.toLong(),
                Sha256Value.calculate(unitEnvelopes[index]),
                unitIndex + 1UL,
                Sha256Value.ZERO,
            )
        }

        private fun publication(
            generation: ULong,
            ciphertext: ByteArray,
            envelope: ByteArray,
            previous: Sha256Value = Sha256Value.ZERO,
        ) =
            RecoveryManifestPublicationRow(
                RUN.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                PublicationKind.MANIFEST,
                generation,
                generation,
                RecoveryRelativeNames.manifestCiphertext(generation),
                ciphertext.size.toLong(),
                Sha256Value.calculate(ciphertext),
                RecoveryRelativeNames.manifestKeyEnvelope(generation),
                envelope.size.toLong(),
                Sha256Value.calculate(envelope),
                previous,
            )
    }

    private fun assertThrowsSource(block: () -> Unit): RecoverySourceAccessException =
        try {
            block()
            error("expected RecoverySourceAccessException")
        } catch (error: RecoverySourceAccessException) {
            error
        }

    private fun bootstrapRow() =
        mapOf(
            "candidate_id" to RecoveryCandidate.MICROFILE.contractId,
            "key_confirmation_relative_name" to "key-confirmation/run.kc",
            "key_confirmation_bytes" to 17L,
            "key_confirmation_sha256" to ByteArray(32) { 0x11 },
            "canonical_alias_sha256" to ByteArray(32) { 0x22 },
        )

    private fun expectedBootstrapIdentity(row: Map<String, Any>) =
        StoredKeyConfirmationIdentity(
            KeyConfirmationValue(RecoveryCandidate.MICROFILE, RUN),
            row.getValue("key_confirmation_relative_name") as String,
            row.getValue("key_confirmation_bytes") as Long,
            Sha256Value.fromBytes(row.getValue("key_confirmation_sha256") as ByteArray),
            Sha256Value.fromBytes(row.getValue("canonical_alias_sha256") as ByteArray),
        )

    private fun storedConfirmation(bytes: ByteArray) =
        StoredKeyConfirmationIdentity(
            KeyConfirmationValue(RecoveryCandidate.MICROFILE, RUN),
            CONFIRMATION_NAME,
            bytes.size.toLong(),
            Sha256Value.calculate(bytes),
            KeyConfirmationValue(RecoveryCandidate.MICROFILE, RUN).canonicalAliasSha256,
        )

    private class CursorProbe(private val rows: List<Map<String, Any>>) : InvocationHandler {
        val getterPositions = mutableListOf<Int>()
        var advanceCount = 0
        private var position = -1
        private val columns =
            listOf(
                "candidate_id",
                "key_confirmation_relative_name",
                "key_confirmation_bytes",
                "key_confirmation_sha256",
                "canonical_alias_sha256",
            )

        val cursor: Cursor =
            Proxy.newProxyInstance(
                Cursor::class.java.classLoader,
                arrayOf(Cursor::class.java),
                this,
            ) as Cursor

        @Suppress("CyclomaticComplexMethod")
        override fun invoke(
            proxy: Any,
            method: java.lang.reflect.Method,
            args: Array<Any?>?,
        ): Any? =
            when (method.name) {
                "getCount" -> rows.size
                "getPosition" -> position
                "moveToFirst" -> moveTo(0)
                "moveToNext" -> {
                    advanceCount += 1
                    moveTo(position + 1)
                }
                "getColumnIndexOrThrow" -> {
                    val column = args!![0] as String
                    columns.indexOf(column).takeIf { it >= 0 }
                        ?: throw IllegalArgumentException("Unknown column: $column")
                }
                "getString" -> value(args).also { getterPositions += position } as String
                "getLong" -> value(args).also { getterPositions += position } as Long
                "getBlob" ->
                    (value(args).also { getterPositions += position } as ByteArray).copyOf()
                "close" -> Unit
                "isClosed" -> false
                "toString" -> "CursorProbe(position=$position)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                else ->
                    throw UnsupportedOperationException("Unexpected cursor call: ${method.name}")
            }

        private fun moveTo(requested: Int): Boolean {
            position =
                when {
                    requested < 0 -> -1
                    requested >= rows.size -> rows.size
                    else -> requested
                }
            return position in rows.indices
        }

        private fun value(args: Array<Any?>?): Any {
            check(position in rows.indices) {
                "Cursor position $position is outside ${rows.size} rows"
            }
            return rows[position].getValue(columns[args!![0] as Int])
        }
    }

    private enum class DescriptorFault {
        OPEN,
        FSTAT_THROW,
        FSTAT_NONREGULAR,
        FSTAT_NEGATIVE_SIZE,
        FSTAT_OVERSIZE,
        READ_THROW,
        PREMATURE_EOF,
        GROWTH,
        NEGATIVE_READ,
        OVERCOUNT_READ,
        CLOSE,
        PREMATURE_EOF_AND_CLOSE,
    }

    private class Descriptor(val path: String, val id: Int) : RecoveryReconciliationDescriptor

    private class InventoryOs : RecoveryReconciliationOs {
        private val stats = mutableMapOf<String, RecoveryReconciliationStat>()
        private val data = mutableMapOf<String, ByteArray>()
        private val children = mutableMapOf<String, MutableList<String>>()
        private val offsets = mutableMapOf<String, Int>()
        val listed = mutableListOf<String>()
        var failRead = false
        private val failedLstat = mutableSetOf<String>()
        private val structuralSecondRead = mutableSetOf<String>()
        private val ioSecondRead = mutableSetOf<String>()
        private val fstatCalls = mutableMapOf<String, Int>()
        private val openCalls = mutableMapOf<String, Int>()
        val events = mutableListOf<String>()
        private var nextDescriptorId = 1
        private var descriptorFaultPath: String? = null
        private var descriptorFault: DescriptorFault? = null

        fun descriptorFault(relative: String, fault: DescriptorFault) {
            descriptorFaultPath = activePath(relative)
            descriptorFault = fault
        }

        fun seed(active: Map<String, ByteArray>, quarantine: Map<String, ByteArray>) {
            val base = File(ROOT, "poc-recovery")
            val v1 = File(base, "v1")
            val activeRoot = File(v1, "runs/${RUN.toCanonicalString()}")
            val quarantineRoot = File(v1, "quarantine/${RUN.toCanonicalString()}")
            val objects = File(quarantineRoot, "objects")
            listOf(
                    ROOT,
                    base,
                    v1,
                    File(v1, "runs"),
                    activeRoot,
                    File(activeRoot, "units"),
                    File(activeRoot, "key-confirmation"),
                    File(v1, "quarantine"),
                    quarantineRoot,
                    objects,
                )
                .forEach {
                    stats[it.path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                }
            active.forEach { (relative, bytes) -> add(activeRoot, relative, bytes) }
            quarantine.forEach { (relative, bytes) -> add(quarantineRoot, relative, bytes) }
        }

        fun addActive(relative: String, bytes: ByteArray) {
            add(File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}"), relative, bytes)
        }

        fun setType(relative: String, type: BootstrapPathType) {
            val path = activePath(relative)
            stats[path] = RecoveryReconciliationStat(type, stats[path]?.size ?: 0)
        }

        fun setSize(relative: String, size: Long) {
            val path = activePath(relative)
            stats[path] = RecoveryReconciliationStat(BootstrapPathType.REGULAR, size)
        }

        fun failLstat(relative: String) {
            failedLstat += activePath(relative)
        }

        fun structuralOnSecondRead(relative: String) {
            structuralSecondRead +=
                File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relative").path
        }

        fun ioOnSecondRead(relative: String) {
            ioSecondRead +=
                File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relative").path
        }

        fun removeQuarantineNamespace() {
            val prefix = File(ROOT, "poc-recovery/v1/quarantine").path
            stats.keys.filter { it.startsWith(prefix) }.forEach(stats::remove)
            children.keys.filter { it.startsWith(prefix) }.forEach(children::remove)
        }

        fun unsafe(relativeDirectory: String) {
            stats[
                File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relativeDirectory")
                    .path] = RecoveryReconciliationStat(BootstrapPathType.SYMLINK)
        }

        fun safeDirectory(relativeDirectory: String) {
            stats[
                File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relativeDirectory")
                    .path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
        }

        fun addUnsafeQuarantine(relative: String) {
            val root = File(ROOT, "poc-recovery/v1/quarantine/${RUN.toCanonicalString()}")
            val parent = File(root, relative).parentFile!!
            children.getOrPut(parent.path) { mutableListOf() } += File(relative).name
            stats[File(root, relative).path] =
                RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
        }

        private fun add(root: File, relative: String, bytes: ByteArray) {
            var parent = root
            val parts = relative.split('/')
            parts.dropLast(1).forEach { name ->
                children
                    .getOrPut(parent.path) { mutableListOf() }
                    .let { if (name !in it) it += name }
                parent = File(parent, name)
                stats[parent.path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
            }
            val leaf = File(parent, parts.last())
            children.getOrPut(parent.path) { mutableListOf() } += leaf.name
            stats[leaf.path] =
                RecoveryReconciliationStat(BootstrapPathType.REGULAR, bytes.size.toLong())
            data[leaf.path] = bytes
        }

        override fun lstat(path: String): RecoveryReconciliationStat? {
            events += "os.lstat:${display(path)}"
            if (path in failedLstat) error("lstat")
            return stats[path]
        }

        override fun list(path: String): List<String> {
            listed += path
            return children[path]?.toList() ?: emptyList()
        }

        override fun mkdir(path: String, mode: Int) = error("not used")

        override fun open(path: String, flags: Int): RecoveryReconciliationDescriptor {
            events += "os.open:${display(path)}"
            if (fault(path, DescriptorFault.OPEN)) error("open")
            offsets[path] = 0
            openCalls[path] = (openCalls[path] ?: 0) + 1
            return Descriptor(path, nextDescriptorId++)
        }

        override fun fstat(
            descriptor: RecoveryReconciliationDescriptor
        ): RecoveryReconciliationStat {
            descriptor as Descriptor
            val path = descriptor.path
            events += "os.fstat:${display(path)}:${descriptor.id}"
            if (fault(path, DescriptorFault.FSTAT_THROW)) error("fstat")
            val call = (fstatCalls[path] ?: 0) + 1
            fstatCalls[path] = call
            val stat = requireNotNull(stats[path])
            return when {
                fault(path, DescriptorFault.FSTAT_NONREGULAR) ->
                    stat.copy(type = BootstrapPathType.SYMLINK)
                fault(path, DescriptorFault.FSTAT_NEGATIVE_SIZE) -> stat.copy(size = -1)
                fault(path, DescriptorFault.FSTAT_OVERSIZE) -> stat.copy(size = 513)
                path in structuralSecondRead && call >= 2 ->
                    stat.copy(size = RecoveryArtifactRoleBounds.maximumFor(File(path).name).plus(1))
                else -> stat
            }
        }

        @Suppress("ReturnCount")
        override fun read(
            descriptor: RecoveryReconciliationDescriptor,
            buffer: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            descriptor as Descriptor
            val path = descriptor.path
            if (failRead || fault(path, DescriptorFault.READ_THROW)) {
                events += "os.read:${display(path)}:${descriptor.id}:throw"
                error("read")
            }
            if (path in ioSecondRead && (openCalls[path] ?: 0) >= 2) {
                events += "os.read:${display(path)}:${descriptor.id}:throw"
                error("read")
            }
            if (
                fault(path, DescriptorFault.PREMATURE_EOF) ||
                    fault(path, DescriptorFault.PREMATURE_EOF_AND_CLOSE)
            ) {
                events += "os.read:${display(path)}:${descriptor.id}:$count:0"
                return 0
            }
            if (fault(path, DescriptorFault.NEGATIVE_READ)) {
                events += "os.read:${display(path)}:${descriptor.id}:$count:-1"
                return -1
            }
            if (fault(path, DescriptorFault.OVERCOUNT_READ)) {
                events += "os.read:${display(path)}:${descriptor.id}:$count:${count + 1}"
                return count + 1
            }
            val bytes = data[path] ?: ByteArray(0)
            val position = offsets[path] ?: 0
            if (position == bytes.size) {
                val result = if (fault(path, DescriptorFault.GROWTH)) 1 else 0
                events += "os.read:${display(path)}:${descriptor.id}:$count:$result"
                return result
            }
            val actual = minOf(count, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + actual)
            offsets[path] = position + actual
            events += "os.read:${display(path)}:${descriptor.id}:$count:$actual"
            return actual
        }

        override fun rename(source: String, destination: String) = error("not used")

        override fun fsync(descriptor: RecoveryReconciliationDescriptor) = error("not used")

        override fun close(descriptor: RecoveryReconciliationDescriptor) {
            descriptor as Descriptor
            events += "os.close:${display(descriptor.path)}:${descriptor.id}"
            if (
                fault(descriptor.path, DescriptorFault.CLOSE) ||
                    fault(descriptor.path, DescriptorFault.PREMATURE_EOF_AND_CLOSE)
            )
                error("close")
        }

        private fun activePath(relative: String) =
            File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relative").path

        private fun fault(path: String, expected: DescriptorFault): Boolean =
            path == descriptorFaultPath && descriptorFault == expected

        private fun display(path: String): String =
            path
                .removePrefix(File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}").path)
                .trimStart(File.separatorChar)
                .replace(File.separatorChar, '/')
    }

    private companion object {
        const val CONFIRMATION_NAME = "key-confirmation/run.kc"
        const val CONFIRMATION_TEMP_NAME = "key-confirmation/run.kc.tmp"
        val ROOT = File("inventory-root").absoluteFile
        val RUN = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

        fun intent(
            source: String,
            bytes: ByteArray,
            state: QuarantineIntentState,
        ): RecoveryQuarantineIntentRow {
            val input =
                RecoveryQuarantineIntentInput(
                    RecoveryCandidate.MICROFILE,
                    RUN,
                    source,
                    RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                    bytes.size.toULong(),
                    Sha256Value.calculate(bytes),
                )
            return RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                state,
            )
        }

        fun tempReferenceRow() =
            RecoveryMicrofileUnitRow(
                RUN.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                1UL,
                0UL,
                1UL,
                5UL,
                "units/u-1.bin.tmp",
                1L,
                Sha256Value.calculate(byteArrayOf(7)),
                "key-envelopes/unit-1.bin",
                1L,
                Sha256Value.calculate(byteArrayOf(8)),
                2UL,
                Sha256Value.calculate(byteArrayOf(9)),
            )
    }
}
