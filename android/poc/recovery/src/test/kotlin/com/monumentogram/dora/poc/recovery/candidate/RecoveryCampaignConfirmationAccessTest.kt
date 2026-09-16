package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.AliasObservation
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationDescriptor
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationOs
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationStat
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCampaignConfirmationAccessTest {
    @Test
    fun `KCF01 actual missing final source becomes typed rejection without alias or artifact open`() {
        for (candidate in RecoveryCandidate.entries) {
            val os = MissingFinalOs()
            val result = loadActual(os, candidate) as RecoveryCampaignConfirmationLoad.MissingFinal
            assertEquals(KeyRecoveryClassification.KEY_CONFIRMATION_MISSING, result.classification)
            assertEquals(
                RecoveryFailureCategory.MISSING_ARTIFACT,
                result.sourceFailure.diagnostic.category,
            )
            assertEquals(RecoveryFailureStage.ARTIFACT_PATH, result.sourceFailure.diagnostic.stage)
            assertEquals(primaryContext, result.sourceFailure.context)
            assertEquals(null, result.sourceFailure.secondaryDiagnostic)
            assertEquals("bootstrap", os.events.first())
            assertEquals("lstat-temp", os.events.last())
            assertTrue(os.events.indexOf("lstat-final") < os.events.indexOf("lstat-temp"))
            assertTrue(os.events.all { it == "bootstrap" || it.startsWith("lstat-") })
        }
    }

    @Test
    fun `KCF01 primary missing final retains actual unsafe or operational temporary diagnostics`() {
        for (unsafe in listOf(false, true)) {
            val os = MissingFinalOs(if (unsafe) "unsafe" else "io")
            val result = loadActual(os) as RecoveryCampaignConfirmationLoad.MissingFinal
            assertEquals(KeyRecoveryClassification.KEY_CONFIRMATION_MISSING, result.classification)
            assertEquals(primaryContext, result.sourceFailure.context)
            assertEquals(
                if (unsafe) RecoveryFailureCategory.CORRUPT_LEAF
                else RecoveryFailureCategory.OPERATIONAL,
                result.sourceFailure.secondaryDiagnostic?.category,
            )
            assertEquals(
                RecoveryArtifactContext.CONFIRMATION_TEMP,
                result.sourceFailure.secondaryContext?.artifactContext,
            )
            assertTrue(os.events.none { it == "alias" || it == "open" || it == "snapshot" })
        }
    }

    @Test
    fun `missing result retains exact source exception and all secondary details`() {
        val secondary =
            diagnostic.copy(
                category = RecoveryFailureCategory.STRUCTURAL,
                artifactSizeLimit =
                    RecoveryArtifactSizeLimitObservation("key-confirmation/run.kc.tmp", 513L, 512L),
            )
        val secondaryContext =
            primaryContext.copy(
                artifactContext = RecoveryArtifactContext.CONFIRMATION_TEMP,
                relativeName = "key-confirmation/run.kc.tmp",
                artifactPresence = RecoveryArtifactPresence.PRESENT,
            )
        val error =
            RecoverySourceAccessException(
                diagnostic,
                IllegalStateException("source"),
                primaryContext,
                secondary,
                secondaryContext,
            )
        val result =
            RecoveryCampaignConfirmationAccess.load { throw error }
                as RecoveryCampaignConfirmationLoad.MissingFinal
        assertSame(error, result.sourceFailure)
        assertSame(secondary, result.sourceFailure.secondaryDiagnostic)
        assertSame(secondaryContext, result.sourceFailure.secondaryContext)
    }

    @Test
    fun `valid source snapshot is returned unchanged without evaluating or fabricating evidence`() {
        val snapshot = KeyConfirmationSnapshot(value, null, null, false, AliasObservation.ABSENT)
        val result =
            RecoveryCampaignConfirmationAccess.load { snapshot }
                as RecoveryCampaignConfirmationLoad.Loaded
        assertSame(snapshot, result.snapshot)
    }

    @Test
    fun `wrong category stage and unexpected typed size never become missing confirmation`() {
        val diagnostics =
            RecoveryFailureCategory.entries
                .filter { it != RecoveryFailureCategory.MISSING_ARTIFACT }
                .map { diagnostic.copy(category = it) } +
                RecoveryFailureStage.entries
                    .filter { it != RecoveryFailureStage.ARTIFACT_PATH }
                    .map { diagnostic.copy(stage = it) } +
                diagnostic.copy(
                    artifactSizeLimit =
                        RecoveryArtifactSizeLimitObservation("key-confirmation/run.kc", 513L, 512L)
                )
        diagnostics.forEach { assertRethrown(failure(it, primaryContext)) }
    }

    @Test
    fun `wrong missing row name role presence and contradictory final context fail closed`() {
        val contexts =
            listOf(
                null,
                primaryContext.copy(bootstrapRowState = null),
                primaryContext.copy(bootstrapRowState = RecoveryBootstrapRowState.UNKNOWN),
                primaryContext.copy(bootstrapRowState = RecoveryBootstrapRowState.ABSENT),
                primaryContext.copy(artifactContext = null),
                primaryContext.copy(artifactContext = RecoveryArtifactContext.CONFIRMATION_TEMP),
                primaryContext.copy(artifactContext = RecoveryArtifactContext.MANIFEST_CIPHERTEXT),
                primaryContext.copy(relativeName = null),
                primaryContext.copy(relativeName = "key-confirmation/run.kc.tmp"),
                primaryContext.copy(relativeName = "../key-confirmation/run.kc"),
                primaryContext.copy(artifactPresence = RecoveryArtifactPresence.UNKNOWN),
                primaryContext.copy(artifactPresence = RecoveryArtifactPresence.PRESENT),
                primaryContext.copy(confirmationFinalPresence = RecoveryArtifactPresence.PRESENT),
            )
        contexts.forEach { assertRethrown(failure(diagnostic, it)) }
    }

    @Test
    fun `untyped framework and IO failures propagate unchanged`() {
        listOf(
                IllegalStateException("closed SQLite"),
                IOException("read"),
                IllegalArgumentException("path"),
            )
            .forEach { error ->
                assertSame(
                    error,
                    assertThrows(error.javaClass) {
                        RecoveryCampaignConfirmationAccess.load { throw error }
                    },
                )
            }
    }

    private fun assertRethrown(error: RecoverySourceAccessException) {
        assertSame(
            error,
            assertThrows(RecoverySourceAccessException::class.java) {
                RecoveryCampaignConfirmationAccess.load { throw error }
            },
        )
    }

    private fun failure(
        diagnostic: RecoveryFailureDiagnostic,
        context: RecoverySourceFailureContext?,
    ) = RecoverySourceAccessException(diagnostic, IllegalStateException("source"), context)

    private fun loadActual(
        os: MissingFinalOs,
        candidate: RecoveryCandidate = RecoveryCandidate.MICROFILE,
    ): RecoveryCampaignConfirmationLoad {
        val expected = KeyConfirmationValue(candidate, run)
        val source =
            AndroidRecoveryReconciliationSource(
                loadBootstrap = {
                    os.events += "bootstrap"
                    StoredKeyConfirmationIdentity(
                        expected,
                        "key-confirmation/run.kc",
                        17L,
                        Sha256Value.calculate(byteArrayOf(1)),
                        expected.canonicalAliasSha256,
                    )
                },
                loadSnapshot = {
                    os.events += "snapshot"
                    error("Must not query candidate")
                },
                loadPending = { error("Must not query pending") },
                loadAllIntents = { error("Must not query intents") },
                storage = AndroidOsRecoveryReconciliationStorage(root, os),
                aliasExists = {
                    os.events += "alias"
                    error("Missing final must not observe alias")
                },
            )
        return RecoveryCampaignConfirmationAccess.load { source.loadConfirmation(run) }
    }

    private class MissingFinalOs(private val temporaryFailure: String? = null) :
        RecoveryReconciliationOs {
        val events = mutableListOf<String>()
        private val paths =
            listOf(
                root,
                File(root, "poc-recovery"),
                File(root, "poc-recovery/v1"),
                File(root, "poc-recovery/v1/runs"),
                File(root, "poc-recovery/v1/runs/${run.toCanonicalString()}"),
                File(root, "poc-recovery/v1/runs/${run.toCanonicalString()}/key-confirmation"),
            )

        override fun lstat(path: String): RecoveryReconciliationStat? {
            val index = paths.indexOfFirst { it.path == path }
            return when {
                index >= 0 -> {
                    events +=
                        "lstat-" +
                            listOf("root", "base", "v1", "runs", "run", "confirmation")[index]
                    RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                }
                path == File(paths.last(), "run.kc").path -> {
                    events += "lstat-final"
                    null
                }
                else -> {
                    check(path == File(paths.last(), "run.kc.tmp").path)
                    events += "lstat-temp"
                    if (temporaryFailure == "io") throw IOException("temporary lstat")
                    if (temporaryFailure == "unsafe")
                        RecoveryReconciliationStat(BootstrapPathType.SYMLINK)
                    else null
                }
            }
        }

        override fun list(path: String): List<String> = error("No inventory")

        override fun mkdir(path: String, mode: Int): Unit = error("No mkdir")

        override fun open(path: String, flags: Int): RecoveryReconciliationDescriptor {
            events += "open"
            error("No open")
        }

        override fun fstat(
            descriptor: RecoveryReconciliationDescriptor
        ): RecoveryReconciliationStat = error("No fstat")

        override fun read(
            descriptor: RecoveryReconciliationDescriptor,
            buffer: ByteArray,
            offset: Int,
            count: Int,
        ): Int = error("No read")

        override fun rename(source: String, destination: String): Unit = error("No rename")

        override fun fsync(descriptor: RecoveryReconciliationDescriptor): Unit = error("No fsync")

        override fun close(descriptor: RecoveryReconciliationDescriptor): Unit = error("No close")
    }

    private companion object {
        val root = File("kcf01-source-root").absoluteFile
        val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val value = KeyConfirmationValue(RecoveryCandidate.MICROFILE, run)
        val primaryContext =
            RecoverySourceFailureContext(
                RecoveryBootstrapRowState.PRESENT,
                RecoveryArtifactContext.CONFIRMATION_FINAL,
                "key-confirmation/run.kc",
                RecoveryArtifactPresence.ABSENT,
            )
        val diagnostic =
            RecoveryFailureDiagnostic(
                RecoveryFailureCategory.MISSING_ARTIFACT,
                "MissingKeyConfirmationArtifact",
                "Missing final",
                RecoveryFailureStage.ARTIFACT_PATH,
            )
    }
}
