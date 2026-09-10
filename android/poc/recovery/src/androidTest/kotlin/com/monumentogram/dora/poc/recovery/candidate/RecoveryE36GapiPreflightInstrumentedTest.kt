@file:Suppress("LargeClass", "LongMethod", "LongParameterList", "MagicNumber", "MaxLineLength")

package com.monumentogram.dora.poc.recovery.candidate

import android.os.Build
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryStreamingSource
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A deliberately gated, synthetic-only E36 Google APIs preflight. It is an instrumentation entry
 * point, not an admission or measured durability campaign.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryE36GapiPreflightInstrumentedTest {
    @Test
    fun syntheticFreshReadbackCleanupReplayAndIdentityDenial() {
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("pocRecoveryE36GapiPreflight") == "true") {
            "pocRecoveryE36GapiPreflight=true is required"
        }
        RecoveryE36GapiDeviceIdentityGuard.requireAccepted(
            RecoveryE36GapiDeviceIdentity(
                api = Build.VERSION.SDK_INT,
                fingerprint = Build.FINGERPRINT,
                product = Build.PRODUCT,
                primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        )
        val revision = requireHarnessRevision(arguments.getString("recoveryHarnessRevision"))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val journal = AndroidRecoveryStreamingJournal(context)
        val runId = RunId.fromBytes(ByteArray(16) { (it + 1).toByte() })
        val sourceBytes = ByteArray(8_192) { ((it * 31 + 9) and 0xff).toByte() }
        val oracleBytes = ByteArray(8_136) { ((it * 7 + 5) and 0xff).toByte() }
        val runDirectory = RecoveryCheckpointAndroidTestFixture.runDirectory(context, runId)
        val sourceFile = File(runDirectory, "stream/stream.ct")
        val events = mutableListOf<RecoveryStreamingEvidenceEvent>()
        val port = CountingAuthenticator(oracleBytes)
        var cleaned = false

        RecoveryCheckpointAndroidTestFixture.cleanupBestEffort(context, runId)
        try {
            val bootstrap = RecoveryCheckpointAndroidTestFixture.bootstrap(context, runId)
            sourceFile.parentFile!!.mkdirs()
            sourceFile.outputStream().use { stream ->
                stream.write(sourceBytes)
                stream.fd.sync()
            }
            val checkpoint = RecoveryCheckpointAndroidTestFixture.checkpoint(runId, sourceBytes)
            val checkpointInsert = journal.insertCheckpoint(checkpoint)
            val checkpointInsertDiagnostic =
                JSONObject().put("resultType", checkpointInsert.javaClass.simpleName)
            when (checkpointInsert) {
                is RecoveryStreamingJournalResult.Retry ->
                    checkpointInsertDiagnostic.put(
                        "classification",
                        checkpointInsert.classification.name,
                    )
                is RecoveryStreamingJournalResult.Fatal ->
                    checkpointInsertDiagnostic.put(
                        "classification",
                        checkpointInsert.classification.name,
                    )
                else -> checkpointInsertDiagnostic.put("classification", JSONObject.NULL)
            }
            println("INSTRUMENTATION_CHECKPOINT_INSERT $checkpointInsertDiagnostic")
            assertTrue(checkpointInsert is RecoveryStreamingJournalResult.CheckpointReceipt)
            checkpointInsert as RecoveryStreamingJournalResult.CheckpointReceipt
            assertEquals(checkpoint.checkpointIdentity, checkpointInsert.checkpointIdentity)
            assertFalse(checkpointInsert.replayed)
            val request = request(checkpoint, runId, oracleBytes, sourceBytes)
            val controller =
                RecoveryStreamingReconciliationController(
                    journal = journal,
                    source = AndroidOsRecoveryStreamingSource(context.noBackupFilesDir, journal),
                    guard = ProcessRecoveryRunSingleWriterGuard,
                    checkpointAuthenticator = port,
                    evidenceSink = RecoveryStreamingEvidenceSink { events += it },
                )

            val fresh = controller.recover(request)
            assertTrue(fresh is RecoveryStreamingReconciliationResult.PersistedValid)
            fresh as RecoveryStreamingReconciliationResult.PersistedValid
            assertFalse(fresh.receipt.replayed)
            assertEquals(oracleBytes.size.toULong(), fresh.recoveredEnd)
            assertEquals(RecoveryStreamingPostReceiptCleanup.NONE, fresh.receipt.postReceiptCleanup)
            assertEquals(
                RecoveryStreamingEvidenceDelivery.DELIVERED,
                fresh.receipt.evidenceDelivery,
            )
            assertEquals(1, port.authenticatorCalls)
            assertEquals(1, port.publicReadCalls)
            val stored = requireNotNull(readOutcome(journal, request))
            assertExactStoredFresh(stored, fresh, request)
            assertEquals(null, readRange(journal, stored.outcomeId))
            assertPersistedEvidence(events.single(), fresh, stored, replayed = false)

            val replay = controller.recover(request)
            assertTrue(replay is RecoveryStreamingReconciliationResult.PersistedValid)
            replay as RecoveryStreamingReconciliationResult.PersistedValid
            assertTrue(replay.receipt.replayed)
            assertEquals(fresh.receipt.outcomeId, replay.receipt.outcomeId)
            assertEquals(fresh.receipt.optionalRangeIntentId, replay.receipt.optionalRangeIntentId)
            assertEquals(
                RecoveryStreamingPostReceiptCleanup.NONE,
                replay.receipt.postReceiptCleanup,
            )
            assertEquals(
                RecoveryStreamingEvidenceDelivery.DELIVERED,
                replay.receipt.evidenceDelivery,
            )
            assertEquals(1, port.authenticatorCalls)
            assertEquals(1, port.publicReadCalls)
            assertExactStoredFresh(requireNotNull(readOutcome(journal, request)), replay, request)
            assertPersistedEvidence(events[1], replay, stored, replayed = true)

            sourceFile.outputStream().use { stream ->
                stream.write(
                    sourceBytes.copyOf().also { it[0] = (it[0].toInt() xor 0xff).toByte() }
                )
                stream.fd.sync()
            }
            val denied = controller.recover(request)
            assertTrue(denied is RecoveryStreamingReconciliationResult.Fatal)
            denied as RecoveryStreamingReconciliationResult.Fatal
            assertEquals(
                RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
                denied.classification,
            )
            assertEquals(1, port.authenticatorCalls)
            assertEquals(1, port.publicReadCalls)
            assertEquals(RecoveryStreamingResultStage.SOURCE_PROOF, denied.stage)
            assertEquals(
                RecoveryStreamingEvidenceEvent.nonPersistable(denied).classification,
                events.last().classification,
            )

            val cleanup = cleanup(context, journal, runId, request, sourceFile, runDirectory)
            cleaned = true
            emitStatus(
                revision,
                context,
                sourceFile,
                port,
                bootstrap,
                fresh,
                replay,
                denied,
                cleanup,
            )
        } finally {
            if (!cleaned) RecoveryCheckpointAndroidTestFixture.cleanupBestEffort(context, runId)
        }
    }

    private fun request(
        checkpoint: RecoveryStreamingCheckpointRow,
        runId: RunId,
        oracle: ByteArray,
        source: ByteArray,
    ): RecoveryStreamingControllerRequest {
        val oracleSha = Sha256Value.calculate(oracle)
        val base =
            RecoveryStreamingWitnessInput(
                runId,
                1UL,
                checkpoint.checkpointIdentity,
                8_192UL,
                4_056UL,
                RecoveryStreamingIdentity.oracle(oracle.size.toULong(), oracleSha, runId),
                oracle.size.toULong(),
                oracleSha,
                source.size.toULong(),
                Sha256Value.calculate(source),
                null,
            )
        val witness =
            base.copy(controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(base))
        return RecoveryStreamingControllerRequest(
            witness,
            RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(witness, oracle),
        )
    }

    private fun readOutcome(
        journal: AndroidRecoveryStreamingJournal,
        request: RecoveryStreamingControllerRequest,
    ) =
        exactJournalValue(
            journal.outcomeByWitness(
                request.witness.runId,
                request.witness.checkpointIdentity,
                RecoveryStreamingIdentity.witness(request.witness),
            )
        )

    private fun readRange(journal: AndroidRecoveryStreamingJournal, outcomeId: Sha256Value) =
        exactJournalValue(journal.rangeByOutcome(outcomeId))

    private fun <T> exactJournalValue(result: RecoveryStreamingJournalReadResult<T>): T =
        when (result) {
            is RecoveryStreamingJournalReadResult.Value -> result.value
            is RecoveryStreamingJournalReadResult.Retry ->
                error("journal-retry:${result.classification.name}")
            is RecoveryStreamingJournalReadResult.Fatal ->
                error("journal-fatal:${result.classification.name}")
        }

    private fun assertExactStoredFresh(
        row: com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow,
        result: RecoveryStreamingReconciliationResult.PersistedValid,
        request: RecoveryStreamingControllerRequest,
    ) {
        assertEquals(result.receipt.outcomeId, row.outcomeId)
        assertEquals(request.witness, row.witness())
        assertEquals(StreamDecision.VALID, row.decision)
        assertEquals(result.acceptedEnd, row.acceptedEnd)
        assertEquals(result.committedEnd, row.checkpointContextEnd)
        assertEquals(result.recoveredEnd, row.recoveredEnd)
        assertEquals(result.terminal, row.terminal)
        assertEquals(null, row.requiredRangeStart)
    }

    private fun assertPersistedEvidence(
        event: RecoveryStreamingEvidenceEvent,
        result: RecoveryStreamingReconciliationResult.PersistedValid,
        row: com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow,
        replayed: Boolean,
    ) {
        assertEquals(null, event.stage)
        assertEquals(null, event.classification)
        assertEquals(null, event.safeExceptionType)
        assertEquals(result.receipt.outcomeId, event.outcomeId)
        assertEquals(null, event.rangeIntentId)
        assertEquals(replayed, event.replayed)
        assertEquals(row.decision, event.persistedDecision)
        assertEquals(row.recoveredEnd, event.recoveredEnd)
        assertEquals(row.terminal, event.terminal)
        assertEquals(RecoveryStreamingPostReceiptCleanup.NONE, event.postReceiptCleanup)
        assertTrue(event.existingEvidenceReferences.isEmpty())
    }

    private fun cleanup(
        context: android.content.Context,
        journal: AndroidRecoveryStreamingJournal,
        runId: RunId,
        request: RecoveryStreamingControllerRequest,
        sourceFile: File,
        runDirectory: File,
    ): RecoveryCheckpointAndroidTestFixture.CleanupObservation {
        val cleanup = RecoveryCheckpointAndroidTestFixture.cleanup(context, runId)
        assertEquals(0, cleanup.rangeDeletes)
        assertEquals(1, cleanup.outcomeDeletes)
        assertEquals(1, cleanup.checkpointDeletes)
        assertEquals(1, cleanup.bootstrapDeletes)
        assertTrue(cleanup.aliasExistedBefore)
        assertTrue(cleanup.aliasAbsentAfter)
        assertTrue(cleanup.runDirectoryAbsentAfter)
        assertEquals(null, readOutcome(journal, request))
        assertTrue(exactJournalValue(journal.checkpointChain(runId)).isEmpty())
        assertFalse(sourceFile.exists())
        assertFalse(runDirectory.exists())
        return cleanup
    }

    private fun emitStatus(
        revision: String,
        context: android.content.Context,
        sourceFile: File,
        port: CountingAuthenticator,
        bootstrap: RecoveryCheckpointAndroidTestFixture.BootstrapObservation,
        fresh: RecoveryStreamingReconciliationResult.PersistedValid,
        replay: RecoveryStreamingReconciliationResult.PersistedValid,
        denied: RecoveryStreamingReconciliationResult.Fatal,
        cleanup: RecoveryCheckpointAndroidTestFixture.CleanupObservation,
    ) {
        val sqlite = AndroidRecoveryJournalDatabase.writable(context)
        val provider = KeyStore.getInstance("AndroidKeyStore").provider.name
        val pragmaNames =
            listOf("journal_mode", "synchronous", "wal_autocheckpoint", "foreign_keys")
        val pragmaObservations =
            pragmaNames.associateWith { name -> observePragma(sqlite, name) }
        val queryFailureClassifications =
            JSONObject()
                .put(
                    "journal_mode",
                    pragmaObservations.getValue("journal_mode").failureClassification
                        ?: JSONObject.NULL,
                )
                .put(
                    "synchronous",
                    pragmaObservations.getValue("synchronous").failureClassification
                        ?: JSONObject.NULL,
                )
                .put(
                    "wal_autocheckpoint",
                    pragmaObservations.getValue("wal_autocheckpoint").failureClassification
                        ?: JSONObject.NULL,
                )
                .put(
                    "foreign_keys",
                    pragmaObservations.getValue("foreign_keys").failureClassification
                        ?: JSONObject.NULL,
                )
        val sqliteDiagnostic =
            JSONObject()
                .put("harnessRevision", revision)
                .put("phase", "post_fixture_cleanup_pre_pragma_assertions")
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("threadId", android.os.Process.myTid())
                .put("currentThreadInTransaction", sqlite.inTransaction())
                .put("databaseOpen", sqlite.isOpen)
                .put("databaseReadOnly", sqlite.isReadOnly)
                .put(
                    "databaseWriteAheadLoggingEnabled",
                    sqlite.isWriteAheadLoggingEnabled,
                )
                .put("connectionIdentity", "UNOBSERVED")
                .put("queryContext", "ordinary_rawQuery")
                .put(
                    "journal_mode",
                    pragmaObservations.getValue("journal_mode").value ?: JSONObject.NULL,
                )
                .put(
                    "synchronous",
                    pragmaObservations.getValue("synchronous").value ?: JSONObject.NULL,
                )
                .put(
                    "wal_autocheckpoint",
                    pragmaObservations.getValue("wal_autocheckpoint").value ?: JSONObject.NULL,
                )
                .put(
                    "foreign_keys",
                    pragmaObservations.getValue("foreign_keys").value ?: JSONObject.NULL,
                )
                .put("queryFailureClassifications", queryFailureClassifications)
        println("INSTRUMENTATION_SQLITE_PRAGMAS_DIAGNOSTIC $sqliteDiagnostic")
        val pragmaFailures =
            pragmaObservations.filterValues { it.failureClassification != null }
        require(pragmaFailures.isEmpty()) {
            "sqlite-pragma-query-failed:${pragmaFailures.keys.sorted().joinToString(",")}"
        }
        val pragmas =
            pragmaObservations.mapValues { (_, observation) -> requireNotNull(observation.value) }
        require(pragmas["journal_mode"].equals("wal", true))
        require(pragmas["synchronous"] in setOf("2", "full"))
        require(pragmas["wal_autocheckpoint"] == "0")
        require(pragmas["foreign_keys"] == "1")
        val instrument = InstrumentationRegistry.getInstrumentation()
        val targetApk = File(context.applicationInfo.sourceDir)
        val testApk = File(instrument.context.applicationInfo.sourceDir)
        val payload =
            JSONObject()
                .put("integratedRuntimePin", "4c318c054da39768340f059db5687051dde8a843")
                .put("harnessRevision", revision)
                .put("sqliteVersion", scalar(sqlite, "select sqlite_version()"))
                .put("sqliteSourceId", scalar(sqlite, "select sqlite_source_id()"))
                .put(
                    "sqliteCompileOptionsSha256",
                    digest(compileOptions(sqlite).joinToString("\n").toByteArray()),
                )
                .put("sqlitePragmas", JSONObject(pragmas))
                .put("keystoreProvider", provider)
                .put("keystoreAlgorithm", KeyProperties.KEY_ALGORITHM_AES)
                .put(
                    "bootstrap",
                    JSONObject()
                        .put(
                            "completedSteps",
                            bootstrap.committed.completedSteps.joinToString(",") { it.name },
                        )
                        .put("evidenceEmitted", bootstrap.committed.evidenceEmitted)
                        .put("runId", bootstrap.evidence.runId)
                        .put("candidateId", bootstrap.evidence.candidateId)
                        .put(
                            "keyConfirmationRelativeName",
                            bootstrap.evidence.keyConfirmationRelativeName,
                        )
                        .put("keyConfirmationBytes", bootstrap.evidence.keyConfirmationBytes)
                        .put(
                            "keyConfirmationSha256",
                            bootstrap.evidence.keyConfirmationSha256.toLowercaseHex(),
                        )
                        .put(
                            "canonicalAliasSha256",
                            bootstrap.evidence.canonicalAliasSha256.toLowercaseHex(),
                        )
                        .put(
                            "keyConfirmationState",
                            bootstrap.evidence.keyConfirmationState.name,
                        ),
                )
                .put(
                    "packages",
                    JSONObject()
                        .put("target", context.packageName)
                        .put("test", instrument.context.packageName),
                )
                .put(
                    "apks",
                    JSONObject()
                        .put("targetSha256", digest(targetApk.readBytes()))
                        .put("testSha256", digest(testApk.readBytes())),
                )
                .put(
                    "device",
                    JSONObject()
                        .put("sdk", Build.VERSION.SDK_INT)
                        .put("fingerprint", Build.FINGERPRINT)
                        .put("product", Build.PRODUCT)
                        .put("model", Build.MODEL)
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("hardware", Build.HARDWARE)
                        .put("abis", Build.SUPPORTED_ABIS.joinToString(",")),
                )
                .put(
                    "source",
                    JSONObject()
                        .put("role", "stream/stream.ct")
                        .put("bytes", 8_192)
                        .put(
                            "sha256",
                            Sha256Value.calculate(
                                    ByteArray(8_192) { ((it * 31 + 9) and 0xff).toByte() }
                                )
                                .toLowercaseHex(),
                        )
                        .put("fsyncSuccess", true)
                        .put("absentAfterCleanup", !sourceFile.exists()),
                )
                .put(
                    "fresh",
                    JSONObject()
                        .put("outcomeId", fresh.receipt.outcomeId.toLowercaseHex())
                        .put("cleanup", fresh.receipt.postReceiptCleanup.name)
                        .put("evidence", fresh.receipt.evidenceDelivery.name),
                )
                .put(
                    "replay",
                    JSONObject()
                        .put("outcomeId", replay.receipt.outcomeId.toLowercaseHex())
                        .put("replayed", replay.receipt.replayed)
                        .put("authenticatorCalls", port.authenticatorCalls)
                        .put("publicReadCalls", port.publicReadCalls),
                )
                .put("negative", requireNotNull(denied.classification).name)
                .put(
                    "cleanup",
                    JSONObject()
                        .put("checkpointDeletes", cleanup.checkpointDeletes)
                        .put("outcomeDeletes", cleanup.outcomeDeletes)
                        .put("rangeDeletes", cleanup.rangeDeletes)
                        .put("bootstrapDeletes", cleanup.bootstrapDeletes)
                        .put("aliasExistedBefore", cleanup.aliasExistedBefore)
                        .put("aliasAbsentAfter", cleanup.aliasAbsentAfter)
                        .put("runDirectoryAbsentAfter", cleanup.runDirectoryAbsentAfter)
                        .put("complete", true),
                )
        println("INSTRUMENTATION_STATUS $payload")
    }

    private fun requireHarnessRevision(value: String?): String =
        requireNotNull(value).also { require(it.matches(Regex("[0-9a-f]{40}"))) }

    private fun pragma(database: android.database.sqlite.SQLiteDatabase, name: String) =
        scalar(database, "PRAGMA $name")

    private fun observePragma(
        database: android.database.sqlite.SQLiteDatabase,
        name: String,
    ): SqlitePragmaObservation =
        try {
            SqlitePragmaObservation(value = pragma(database, name), failureClassification = null)
        } catch (error: RuntimeException) {
            val classification =
                when (error) {
                    is android.database.sqlite.SQLiteException -> "SQLITE_EXCEPTION"
                    is IllegalStateException -> "ILLEGAL_STATE"
                    else -> "RUNTIME_EXCEPTION"
                }
            SqlitePragmaObservation(value = null, failureClassification = classification)
        }

    private fun scalar(database: android.database.sqlite.SQLiteDatabase, sql: String): String =
        database.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun compileOptions(database: android.database.sqlite.SQLiteDatabase): List<String> =
        database.rawQuery("PRAGMA compile_options", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }.sorted()
        }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class SqlitePragmaObservation(
        val value: String?,
        val failureClassification: String?,
    )

    private class CountingAuthenticator(private val oracle: ByteArray) :
        RecoveryStreamingCheckpointAuthenticator {
        var authenticatorCalls = 0
        var publicReadCalls = 0

        override fun authenticate(
            checkpoint: RecoveryStreamingCheckpointRow,
            witness: RecoveryStreamingWitnessInput,
        ): RecoveryStreamingCheckpointAuthentication {
            authenticatorCalls += 1
            return RecoveryStreamingCheckpointAuthentication.Ready(
                RecoveryStreamingPublicStreamOpener { _, _ ->
                    publicReadCalls += 1
                    oracle.inputStream().let { input ->
                        object : RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead {
                            override fun read(destination: ByteArray, offset: Int, count: Int) =
                                input.read(destination, offset, count)

                            override fun close() = input.close()
                        }
                    }
                }
            )
        }
    }
}
